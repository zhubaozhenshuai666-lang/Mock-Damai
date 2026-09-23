package com.zewbby.smartticket.service.impl;

import com.zewbby.smartticket.auth.UserContext;
import com.zewbby.smartticket.common.BusinessException;
import com.zewbby.smartticket.config.PurchasePlanProperties;
import com.zewbby.smartticket.domain.dto.CompletePurchasePlanRequest;
import com.zewbby.smartticket.domain.dto.ConfirmPurchasePlanRequest;
import com.zewbby.smartticket.domain.dto.CreateOrderRequest;
import com.zewbby.smartticket.domain.dto.CreatePurchasePlanRequest;
import com.zewbby.smartticket.domain.dto.OrderSnapshot;
import com.zewbby.smartticket.domain.dto.SubmitPurchasePlanRequest;
import com.zewbby.smartticket.domain.dto.UpdatePurchasePlanAudienceRequest;
import com.zewbby.smartticket.domain.dto.UpdatePurchasePlanSpecRequest;
import com.zewbby.smartticket.domain.entity.AudiencePerson;
import com.zewbby.smartticket.domain.entity.PerformanceSession;
import com.zewbby.smartticket.domain.entity.ShowInfo;
import com.zewbby.smartticket.domain.entity.TicketPurchasePlan;
import com.zewbby.smartticket.domain.entity.TicketPurchasePlanAudience;
import com.zewbby.smartticket.domain.vo.OrderRequestVO;
import com.zewbby.smartticket.domain.vo.TicketPurchasePlanVO;
import com.zewbby.smartticket.enums.AudienceStatusEnum;
import com.zewbby.smartticket.enums.PurchasePlanAudienceSelectionTypeEnum;
import com.zewbby.smartticket.enums.PurchasePlanStatusEnum;
import com.zewbby.smartticket.enums.ShowStatusEnum;
import com.zewbby.smartticket.mapper.AudiencePersonMapper;
import com.zewbby.smartticket.mapper.ShowMapper;
import com.zewbby.smartticket.mapper.TicketCategoryMapper;
import com.zewbby.smartticket.mapper.TicketPurchasePlanAudienceMapper;
import com.zewbby.smartticket.mapper.TicketPurchasePlanMapper;
import com.zewbby.smartticket.service.ArtistRankingService;
import com.zewbby.smartticket.service.AsyncOrderSubmissionRejectedException;
import com.zewbby.smartticket.service.OrderService;
import com.zewbby.smartticket.service.ShowRelationCacheService;
import com.zewbby.smartticket.service.TicketPurchasePlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TicketPurchasePlanServiceImpl implements TicketPurchasePlanService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TicketPurchasePlanServiceImpl.class);

    private final TicketPurchasePlanMapper planMapper;
    private final TicketPurchasePlanAudienceMapper planAudienceMapper;
    private final AudiencePersonMapper audiencePersonMapper;
    private final ShowMapper showMapper;
    private final TicketCategoryMapper ticketCategoryMapper;
    private final ShowRelationCacheService showRelationCacheService;
    private final OrderService orderService;
    private final PurchasePlanProperties purchasePlanProperties;

    @Autowired(required = false)
    private ArtistRankingService artistRankingService;

    public TicketPurchasePlanServiceImpl(TicketPurchasePlanMapper planMapper,
                                         TicketPurchasePlanAudienceMapper planAudienceMapper,
                                         AudiencePersonMapper audiencePersonMapper,
                                         ShowMapper showMapper,
                                         TicketCategoryMapper ticketCategoryMapper,
                                         ShowRelationCacheService showRelationCacheService,
                                         OrderService orderService,
                                         PurchasePlanProperties purchasePlanProperties) {
        this.planMapper = planMapper;
        this.planAudienceMapper = planAudienceMapper;
        this.audiencePersonMapper = audiencePersonMapper;
        this.showMapper = showMapper;
        this.ticketCategoryMapper = ticketCategoryMapper;
        this.showRelationCacheService = showRelationCacheService;
        this.orderService = orderService;
        this.purchasePlanProperties = purchasePlanProperties;
    }

    @Override
    @Transactional
    public TicketPurchasePlanVO create(CreatePurchasePlanRequest request) {
        ensureEnabled();
        Long userId = UserContext.requireUserId();
        ShowInfo show = showMapper.selectShowInfoById(request.getShowId());
        if (show == null || !ShowStatusEnum.PUBLISHED.getCode().equals(show.getStatus())) {
            throw new BusinessException("演出不存在");
        }
        List<Long> audienceIds = normalizeIds(request.getDefaultAudienceIds());
        ensureQuantityAllowed(audienceIds.size());
        List<AudiencePerson> audiences = loadActiveAudiences(userId, audienceIds);
        LocalDateTime now = LocalDateTime.now();

        TicketPurchasePlan plan = new TicketPurchasePlan();
        plan.setPlanNo("PLAN" + UUID.randomUUID().toString().replace("-", ""));
        plan.setUserId(userId);
        plan.setShowId(request.getShowId());
        plan.setStatus(PurchasePlanStatusEnum.DRAFT.getCode());
        plan.setVersion(0);
        plan.setDefaultAudienceCount(audiences.size());
        plan.setSelectedAudienceCount(audiences.size());
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        if (planMapper.insert(plan) != 1) {
            throw new BusinessException("预约计划创建失败");
        }

        List<TicketPurchasePlanAudience> defaults = toPlanAudiences(
                plan.getId(), audiences, PurchasePlanAudienceSelectionTypeEnum.DEFAULT.getCode(), now);
        List<TicketPurchasePlanAudience> selected = toPlanAudiences(
                plan.getId(), audiences, PurchasePlanAudienceSelectionTypeEnum.SELECTED.getCode(), now);
        if (planAudienceMapper.insertBatch(defaults) != defaults.size()
                || planAudienceMapper.insertBatch(selected) != selected.size()) {
            throw new BusinessException("预约观演人保存失败");
        }
        return toVO(plan);
    }

    @Override
    public TicketPurchasePlanVO get(Long planId) {
        return toVO(getOwnedPlan(planId));
    }

    @Override
    public List<TicketPurchasePlanVO> list(String status, Integer limit) {
        int safeLimit = normalizeListLimit(limit);
        String normalizedStatus = normalizeStatus(status);
        return planMapper.selectByUserId(UserContext.requireUserId(), normalizedStatus, safeLimit)
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TicketPurchasePlanVO updateSpec(Long planId, UpdatePurchasePlanSpecRequest request) {
        Long userId = UserContext.requireUserId();
        TicketPurchasePlan plan = getOwnedPlan(planId);
        ensureEditable(plan);
        ensureQuantityAllowed(request.getQuantity());
        validateRelation(plan.getShowId(), request.getSessionId(), request.getTicketCategoryId());
        PerformanceSession session = requireSession(request.getSessionId());
        ensureBeforeSaleStart(session);
        String activePlanKey = activePlanKey(userId, request.getSessionId());
        try {
            if (planMapper.updateSpec(
                    planId,
                    userId,
                    request.getSessionId(),
                    request.getTicketCategoryId(),
                    request.getQuantity(),
                    activePlanKey,
                    request.getVersion(),
                    LocalDateTime.now(),
                    LocalDateTime.now()) != 1) {
                throw new BusinessException("预约计划已被修改，请刷新后重试");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("同一场次只能保留一份有效预约");
        }
        return get(planId);
    }

    @Override
    @Transactional
    public TicketPurchasePlanVO complete(Long planId, CompletePurchasePlanRequest request) {
        Long userId = UserContext.requireUserId();
        TicketPurchasePlan plan = getOwnedPlan(planId);
        if (!PurchasePlanStatusEnum.DRAFT.getCode().equals(plan.getStatus())) {
            throw new BusinessException("预约方案已完成或不可修改");
        }
        if (plan.getSessionId() == null || plan.getTicketCategoryId() == null || plan.getQuantity() == null) {
            throw new BusinessException("请先选择场次、票档和数量");
        }
        validateRelation(plan.getShowId(), plan.getSessionId(), plan.getTicketCategoryId());
        PerformanceSession session = requireSession(plan.getSessionId());
        ensureBeforeSaleStart(session);
        List<TicketPurchasePlanAudience> selected = selectedAudiences(planId);
        if (selected.size() != plan.getQuantity()) {
            throw new BusinessException("观演人数必须与购票数量一致");
        }
        LocalDateTime now = LocalDateTime.now();
        int completedVersion = plan.getVersion() + 1;
        if (planMapper.complete(planId, userId, request.getVersion(), completedVersion, now, now) != 1) {
            throw new BusinessException("预约计划已被修改，请刷新后重试");
        }
        return get(planId);
    }

    @Override
    @Transactional
    public TicketPurchasePlanVO confirmSpec(Long planId, ConfirmPurchasePlanRequest request) {
        return complete(planId, new CompletePurchasePlanRequest(request.getVersion()));
    }

    @Override
    @Transactional
    public TicketPurchasePlanVO updateAudiences(Long planId, UpdatePurchasePlanAudienceRequest request) {
        Long userId = UserContext.requireUserId();
        TicketPurchasePlan plan = getOwnedPlan(planId);
        ensureEditable(plan);
        if (plan.getSessionId() == null || plan.getQuantity() == null) {
            throw new BusinessException("请先选择场次和购票数量");
        }
        PerformanceSession session = requireSession(plan.getSessionId());
        ensureBeforeSaleStart(session);
        List<Long> audienceIds = normalizeIds(request.getAudienceIds());
        if (!Integer.valueOf(audienceIds.size()).equals(plan.getQuantity())) {
            throw new BusinessException("观演人数必须与购票数量一致");
        }
        List<AudiencePerson> audiences = loadActiveAudiences(userId, audienceIds);
        LocalDateTime now = LocalDateTime.now();
        if (planMapper.updateAudiences(
                planId, userId, request.getVersion(), audienceIds.size(), now, now) != 1) {
            throw new BusinessException("预约计划已被修改，请刷新后重试");
        }
        planAudienceMapper.deleteByPlanIdAndSelectionType(
                planId, PurchasePlanAudienceSelectionTypeEnum.SELECTED.getCode());
        List<TicketPurchasePlanAudience> selected = toPlanAudiences(
                planId, audiences, PurchasePlanAudienceSelectionTypeEnum.SELECTED.getCode(), now);
        if (planAudienceMapper.insertBatch(selected) != selected.size()) {
            throw new BusinessException("预约观演人保存失败");
        }
        return get(planId);
    }

    @Override
    @Transactional(noRollbackFor = RuntimeException.class)
    public OrderRequestVO submit(Long planId, SubmitPurchasePlanRequest request) {
        ensureEnabled();
        Long userId = UserContext.requireUserId();
        TicketPurchasePlan plan = getOwnedPlan(planId);
        if (isExistingSubmission(plan, request.getIdempotencyToken())) {
            return orderService.getOrderRequestResult(plan.getOrderRequestId());
        }
        if (PurchasePlanStatusEnum.RECONCILIATION_REQUIRED.getCode().equals(plan.getStatus())) {
            throw new BusinessException("预约请求正在对账，请稍后重试");
        }
        if (!PurchasePlanStatusEnum.isSubmittable(plan.getStatus())) {
            throw new BusinessException("请先完成预约方案");
        }
        if (plan.getSessionId() == null || plan.getTicketCategoryId() == null
                || plan.getQuantity() == null || plan.getCompletedVersion() == null) {
            throw new BusinessException("预约计划缺少已完成方案");
        }
        PerformanceSession session = requireSession(plan.getSessionId());
        ensureWithinSaleWindow(session);
        validateRelation(plan.getShowId(), plan.getSessionId(), plan.getTicketCategoryId());
        List<TicketPurchasePlanAudience> selected = selectedAudiences(planId);
        if (selected.size() != plan.getQuantity()) {
            throw new BusinessException("预约观演人快照与购票数量不一致");
        }

        LocalDateTime now = LocalDateTime.now();
        String requestId = "PLANREQ-" + planId + "-" + UUID.randomUUID().toString().replace("-", "");
        if (planMapper.beginSubmitting(
                planId,
                userId,
                request.getVersion(),
                PurchasePlanStatusEnum.SUBMITTING.getCode(),
                request.getIdempotencyToken(),
                requestId,
                now,
                now,
                now) != 1) {
            throw new BusinessException("预约计划已提交或已被修改，请刷新后重试");
        }

        CreateOrderRequest orderRequest = new CreateOrderRequest(
                userId,
                plan.getShowId(),
                plan.getSessionId(),
                plan.getTicketCategoryId(),
                plan.getQuantity(),
                request.getIdempotencyToken());
        orderRequest.setRequestId(requestId);
        orderRequest.setAdmissionToken(request.getAdmissionToken());
        orderRequest.setPurchasePlanId(planId);
        try {
            OrderRequestVO result = orderService.submitAsyncOrder(orderRequest);
            if (result == null || !requestId.equals(result.getRequestId())) {
                throw new BusinessException("异步创单请求标识不一致");
            }
            recordPurchaseIntent(plan);
            return result;
        } catch (RuntimeException exception) {
            LocalDateTime failedAt = LocalDateTime.now();
            String reason = exception.getMessage() == null ? "异步创单结果未知，等待对账" : exception.getMessage();
            if (exception instanceof AsyncOrderSubmissionRejectedException) {
                int failedRows = planMapper.markFailed(
                        planId, requestId, PurchasePlanStatusEnum.FAILED.getCode(), reason, failedAt);
                if (failedRows != 1) {
                    int reconciliationRows = planMapper.markReconciliationRequired(
                            planId,
                            requestId,
                            PurchasePlanStatusEnum.RECONCILIATION_REQUIRED.getCode(),
                            reason,
                            LocalDateTime.now());
                    if (reconciliationRows != 1) {
                        LOGGER.error("预约计划同步拒绝后状态收敛失败，planId={}, requestId={}",
                                planId, requestId, exception);
                    }
                }
            } else {
                int rows = planMapper.markReconciliationRequired(
                        planId,
                        requestId,
                        PurchasePlanStatusEnum.RECONCILIATION_REQUIRED.getCode(),
                        reason,
                        failedAt);
                if (rows != 1) {
                    LOGGER.error("预约计划进入对账状态失败，planId={}, requestId={}", planId, requestId, exception);
                }
            }
            throw exception;
        }
    }

    private boolean isExistingSubmission(TicketPurchasePlan plan, String idempotencyToken) {
        return (PurchasePlanStatusEnum.SUBMITTING.getCode().equals(plan.getStatus())
                || PurchasePlanStatusEnum.ORDER_CREATED.getCode().equals(plan.getStatus()))
                && plan.getOrderRequestId() != null
                && idempotencyToken != null
                && idempotencyToken.equals(plan.getIdempotencyToken());
    }

    private void recordPurchaseIntent(TicketPurchasePlan plan) {
        if (artistRankingService == null || plan == null || plan.getShowId() == null) {
            return;
        }
        try {
            ShowInfo show = showMapper.selectShowInfoById(plan.getShowId());
            if (show != null) {
                artistRankingService.recordPurchaseIntent(show.getArtist(), null);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("记录艺人购票意向排行榜信号失败，planId={}", plan.getId(), exception);
        }
    }

    @Override
    public TicketPurchasePlanVO retryFailed(Long planId, Integer version) {
        TicketPurchasePlan plan = getOwnedPlan(planId);
        if (!PurchasePlanStatusEnum.FAILED.getCode().equals(plan.getStatus())) {
            throw new BusinessException("只有失败的预约计划才能重试");
        }
        return toVO(plan);
    }

    @Override
    @Transactional
    public void cancel(Long planId, Integer version) {
        Long userId = UserContext.requireUserId();
        TicketPurchasePlan plan = getOwnedPlan(planId);
        if (!PurchasePlanStatusEnum.DRAFT.getCode().equals(plan.getStatus())
                && !PurchasePlanStatusEnum.READY.getCode().equals(plan.getStatus())
                && !PurchasePlanStatusEnum.FAILED.getCode().equals(plan.getStatus())) {
            throw new BusinessException("当前预约状态不能取消");
        }
        if (plan.getSessionId() != null && !PurchasePlanStatusEnum.FAILED.getCode().equals(plan.getStatus())
                && !isBeforeSaleStart(requireSession(plan.getSessionId()))) {
            throw new BusinessException("场次已开售，预约方案不能取消");
        }
        if (planMapper.cancelEditable(
                planId, userId, version, PurchasePlanStatusEnum.CANCELLED.getCode(), LocalDateTime.now()) != 1) {
            throw new BusinessException("预约计划已被修改，请刷新后重试");
        }
    }

    private TicketPurchasePlan getOwnedPlan(Long planId) {
        TicketPurchasePlan plan = planMapper.selectByIdAndUserId(planId, UserContext.requireUserId());
        if (plan == null) {
            throw new BusinessException("预约计划不存在");
        }
        return plan;
    }

    private void ensureEditable(TicketPurchasePlan plan) {
        if (!PurchasePlanStatusEnum.isEditable(plan.getStatus())) {
            throw new BusinessException("当前预约方案不可编辑");
        }
    }

    private PerformanceSession requireSession(Long sessionId) {
        PerformanceSession session = showMapper.selectSessionById(sessionId);
        if (session == null) {
            throw new BusinessException("场次不存在");
        }
        return session;
    }

    private void validateRelation(Long showId, Long sessionId, Long ticketCategoryId) {
        if (showRelationCacheService != null) {
            showRelationCacheService.validatePublishedRelation(showId, sessionId, ticketCategoryId);
            return;
        }
        if (!ticketCategoryMapper.existsShowSessionTicketCategoryRelation(showId, sessionId, ticketCategoryId)) {
            throw new BusinessException("演出、场次和票档不匹配");
        }
    }

    private void ensureBeforeSaleStart(PerformanceSession session) {
        if (!isBeforeSaleStart(session)) {
            throw new BusinessException("场次已开售，预约方案不可修改");
        }
        if (!hasSaleWindow(session)) {
            throw new BusinessException("场次未配置开售时间窗口");
        }
    }

    private boolean isBeforeSaleStart(PerformanceSession session) {
        return session.getSaleStartTime() != null && session.getSaleStartTime().isAfter(LocalDateTime.now());
    }

    private void ensureWithinSaleWindow(PerformanceSession session) {
        if (!hasSaleWindow(session)) {
            throw new BusinessException("场次未配置开售时间窗口");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(session.getSaleStartTime()) || !now.isBefore(session.getSaleEndTime())) {
            throw new BusinessException("当前不在开售时间窗口");
        }
    }

    private boolean hasSaleWindow(PerformanceSession session) {
        return session.getSaleStartTime() != null && session.getSaleEndTime() != null
                && session.getSaleStartTime().isBefore(session.getSaleEndTime());
    }

    private List<TicketPurchasePlanAudience> selectedAudiences(Long planId) {
        List<TicketPurchasePlanAudience> selected = planAudienceMapper.selectByPlanIdAndSelectionType(
                planId, PurchasePlanAudienceSelectionTypeEnum.SELECTED.getCode());
        return selected == null ? Collections.emptyList() : selected;
    }

    private List<AudiencePerson> loadActiveAudiences(Long userId, List<Long> ids) {
        List<AudiencePerson> audiences = audiencePersonMapper.selectByIdsAndUserId(ids, userId);
        if (audiences == null || audiences.size() != ids.size()
                || audiences.stream().anyMatch(item -> !AudienceStatusEnum.ACTIVE.getCode().equals(item.getStatus()))) {
            throw new BusinessException("存在无效的实名观演人");
        }
        return audiences;
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请至少选择一名实名观演人");
        }
        List<Long> normalized = ids.stream().distinct().toList();
        if (normalized.size() != ids.size() || normalized.stream().anyMatch(item -> item == null || item <= 0)) {
            throw new BusinessException("实名观演人选择无效");
        }
        return normalized;
    }

    private String activePlanKey(Long userId, Long sessionId) {
        return userId + ":" + sessionId;
    }

    private void ensureQuantityAllowed(int quantity) {
        if (quantity <= 0 || quantity > purchasePlanProperties.safeMaxQuantity()) {
            throw new BusinessException("购票数量必须在1至" + purchasePlanProperties.safeMaxQuantity() + "张之间");
        }
    }

    private void ensureEnabled() {
        if (!purchasePlanProperties.isEnabled()) {
            throw new BusinessException("预约购票功能暂未开放");
        }
    }

    private int normalizeListLimit(Integer limit) {
        int value = limit == null ? 20 : limit;
        if (value <= 0) {
            throw new BusinessException("查询条数必须大于0");
        }
        return Math.min(value, 50);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        for (PurchasePlanStatusEnum item : PurchasePlanStatusEnum.values()) {
            if (item.getCode().equals(normalized)) {
                return normalized;
            }
        }
        throw new BusinessException("预约状态无效");
    }

    private List<TicketPurchasePlanAudience> toPlanAudiences(Long planId,
                                                              List<AudiencePerson> audiences,
                                                              String selectionType,
                                                              LocalDateTime now) {
        return java.util.stream.IntStream.range(0, audiences.size())
                .mapToObj(index -> {
                    AudiencePerson item = audiences.get(index);
                    return new TicketPurchasePlanAudience(
                            null, planId, item.getId(), selectionType, index + 1,
                            item.getName(), item.getIdNoHash(), now);
                })
                .toList();
    }

    private TicketPurchasePlanVO toVO(TicketPurchasePlan plan) {
        TicketPurchasePlanVO vo = new TicketPurchasePlanVO();
        vo.setId(plan.getId());
        vo.setPlanNo(plan.getPlanNo());
        vo.setUserId(plan.getUserId());
        vo.setShowId(plan.getShowId());
        vo.setSessionId(plan.getSessionId());
        vo.setTicketCategoryId(plan.getTicketCategoryId());
        vo.setQuantity(plan.getQuantity());
        vo.setStatus(plan.getStatus());
        vo.setVersion(plan.getVersion());
        vo.setCompletedVersion(plan.getCompletedVersion());
        vo.setDefaultAudienceCount(plan.getDefaultAudienceCount());
        vo.setSelectedAudienceCount(plan.getSelectedAudienceCount());
        vo.setDefaultAudienceIds(audienceIds(plan.getId(), PurchasePlanAudienceSelectionTypeEnum.DEFAULT.getCode()));
        vo.setSelectedAudienceIds(audienceIds(plan.getId(), PurchasePlanAudienceSelectionTypeEnum.SELECTED.getCode()));
        if (plan.getShowId() != null && plan.getSessionId() != null && plan.getTicketCategoryId() != null) {
            OrderSnapshot snapshot = ticketCategoryMapper.selectOrderSnapshot(
                    plan.getShowId(), plan.getSessionId(), plan.getTicketCategoryId());
            if (snapshot != null) {
                vo.setTicketPrice(snapshot.getTicketPrice());
                if (plan.getQuantity() != null && snapshot.getTicketPrice() != null) {
                    vo.setTotalAmount(snapshot.getTicketPrice().multiply(BigDecimal.valueOf(plan.getQuantity())));
                }
            }
        }
        vo.setExpireTime(plan.getExpireTime());
        vo.setConfirmedAt(plan.getConfirmedAt());
        vo.setCompletedAt(plan.getCompletedAt());
        vo.setSubmittedAt(plan.getSubmittedAt());
        vo.setOrderRequestId(plan.getOrderRequestId());
        vo.setOrderId(plan.getOrderId());
        vo.setFailReason(plan.getFailReason());
        vo.setCreatedAt(plan.getCreatedAt());
        vo.setUpdatedAt(plan.getUpdatedAt());
        return vo;
    }

    private List<Long> audienceIds(Long planId, String selectionType) {
        List<TicketPurchasePlanAudience> rows = planAudienceMapper.selectByPlanIdAndSelectionType(planId, selectionType);
        return rows == null ? List.of() : rows.stream().map(TicketPurchasePlanAudience::getAudienceId).toList();
    }
}

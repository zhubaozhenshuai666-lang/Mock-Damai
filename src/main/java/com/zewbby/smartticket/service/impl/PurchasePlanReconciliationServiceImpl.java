package com.zewbby.smartticket.service.impl;

import com.zewbby.smartticket.config.PurchasePlanProperties;
import com.zewbby.smartticket.domain.entity.TicketOrderRequest;
import com.zewbby.smartticket.domain.entity.TicketPurchasePlan;
import com.zewbby.smartticket.enums.CompensationStatusEnum;
import com.zewbby.smartticket.enums.OrderRequestStatusEnum;
import com.zewbby.smartticket.enums.PurchasePlanStatusEnum;
import com.zewbby.smartticket.mapper.OrderRequestMapper;
import com.zewbby.smartticket.mapper.TicketPurchasePlanMapper;
import com.zewbby.smartticket.service.PurchasePlanReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PurchasePlanReconciliationServiceImpl implements PurchasePlanReconciliationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PurchasePlanReconciliationServiceImpl.class);

    private final TicketPurchasePlanMapper planMapper;

    private final OrderRequestMapper orderRequestMapper;

    private final PurchasePlanProperties properties;

    public PurchasePlanReconciliationServiceImpl(TicketPurchasePlanMapper planMapper,
                                                  OrderRequestMapper orderRequestMapper,
                                                  PurchasePlanProperties properties) {
        this.planMapper = planMapper;
        this.orderRequestMapper = orderRequestMapper;
        this.properties = properties;
    }

    @Override
    public void reconcileStalePlans() {
        if (!properties.isReconciliationEnabled()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minusNanos(properties.safeReconciliationTimeoutMillis() * 1_000_000L);
        List<TicketPurchasePlan> stalePlans;
        try {
            stalePlans = planMapper.selectStaleForReconciliation(
                    staleBefore, properties.safeReconciliationBatchSize());
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to select stale purchase plans for reconciliation", exception);
            return;
        }
        if (stalePlans == null || stalePlans.isEmpty()) {
            return;
        }

        for (TicketPurchasePlan plan : stalePlans) {
            try {
                reconcile(plan, LocalDateTime.now());
            } catch (RuntimeException exception) {
                LOGGER.error("Purchase plan reconciliation failed, planId={}, requestId={}",
                        plan == null ? null : plan.getId(),
                        plan == null ? null : plan.getOrderRequestId(),
                        exception);
            }
        }
    }

    private void reconcile(TicketPurchasePlan plan, LocalDateTime now) {
        if (plan == null || plan.getId() == null) {
            LOGGER.error("Cannot reconcile stale purchase plan because the plan row has no id");
            return;
        }
        String requestId = plan.getOrderRequestId();
        if (requestId == null || requestId.isBlank()) {
            LOGGER.error("Stale purchase plan has no order request id, planId={}, status={}",
                    plan.getId(), plan.getStatus());
            return;
        }

        TicketOrderRequest orderRequest = orderRequestMapper.selectByRequestId(requestId);
        if (orderRequest == null) {
            retainForReconciliation(plan, requestId, "异步下单请求记录不存在，等待人工或后续对账", now);
            return;
        }

        String status = orderRequest.getStatus();
        if (OrderRequestStatusEnum.SUCCESS.getCode().equals(status)) {
            if (orderRequest.getOrderId() == null) {
                retainForReconciliation(plan, requestId, "下单请求已成功但缺少订单ID，等待人工核查", now);
                return;
            }
            int rows = planMapper.markOrderCreated(
                    plan.getId(), requestId, orderRequest.getOrderId(),
                    PurchasePlanStatusEnum.ORDER_CREATED.getCode(), now);
            if (rows == 0) {
                LOGGER.warn("Successful order request did not update its purchase plan, planId={}, requestId={}, orderId={}",
                        plan.getId(), requestId, orderRequest.getOrderId());
            }
            return;
        }

        if (isFailed(status)) {
            if (isSafeToMarkFailed(orderRequest)) {
                String reason = hasText(orderRequest.getFailReason())
                        ? orderRequest.getFailReason()
                        : "异步下单失败，库存预扣已确认安全";
                int rows = planMapper.markFailed(
                        plan.getId(), requestId, PurchasePlanStatusEnum.FAILED.getCode(), reason, now);
                if (rows == 0) {
                    LOGGER.warn("Safely failed order request did not update its purchase plan, planId={}, requestId={}",
                            plan.getId(), requestId);
                }
                return;
            }
            retainForReconciliation(plan, requestId,
                    "下单请求失败但库存预扣补偿未确认，禁止释放预约计划", now);
            return;
        }

        if (isInFlight(status)) {
            LOGGER.debug("Purchase plan order request remains in flight; reconciliation will retry later, planId={}, requestId={}, status={}",
                    plan.getId(), requestId, status);
            return;
        }

        retainForReconciliation(plan, requestId,
                "下单请求状态无法安全收敛，status=" + status, now);
    }

    private boolean isFailed(String status) {
        return OrderRequestStatusEnum.FAILED.getCode().equals(status)
                || OrderRequestStatusEnum.COMPENSATED.getCode().equals(status);
    }

    private boolean isSafeToMarkFailed(TicketOrderRequest request) {
        if (!Boolean.TRUE.equals(request.getRedisDeducted())) {
            return true;
        }
        return Boolean.TRUE.equals(request.getCompensated())
                || CompensationStatusEnum.COMPENSATED.getCode().equals(request.getCompensationStatus());
    }

    private boolean isInFlight(String status) {
        return OrderRequestStatusEnum.QUEUED.getCode().equals(status)
                || OrderRequestStatusEnum.PRE_DEDUCTED.getCode().equals(status)
                || OrderRequestStatusEnum.PROCESSING.getCode().equals(status);
    }

    private void retainForReconciliation(TicketPurchasePlan plan,
                                         String requestId,
                                         String reason,
                                         LocalDateTime now) {
        int rows = planMapper.markReconciliationRequired(
                plan.getId(), requestId, PurchasePlanStatusEnum.RECONCILIATION_REQUIRED.getCode(), reason, now);
        LOGGER.warn("Purchase plan requires reconciliation, planId={}, requestId={}, reason={}, updated={}",
                plan.getId(), requestId, reason, rows == 1);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

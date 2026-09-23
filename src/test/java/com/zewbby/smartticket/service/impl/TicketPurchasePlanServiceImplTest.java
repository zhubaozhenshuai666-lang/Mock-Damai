package com.zewbby.smartticket.service.impl;

import com.zewbby.smartticket.auth.UserContext;
import com.zewbby.smartticket.config.PurchasePlanProperties;
import com.zewbby.smartticket.domain.dto.CompletePurchasePlanRequest;
import com.zewbby.smartticket.domain.dto.CreatePurchasePlanRequest;
import com.zewbby.smartticket.domain.dto.SubmitPurchasePlanRequest;
import com.zewbby.smartticket.domain.dto.UpdatePurchasePlanAudienceRequest;
import com.zewbby.smartticket.domain.dto.UpdatePurchasePlanSpecRequest;
import com.zewbby.smartticket.domain.entity.AudiencePerson;
import com.zewbby.smartticket.domain.entity.PerformanceSession;
import com.zewbby.smartticket.domain.entity.ShowInfo;
import com.zewbby.smartticket.domain.entity.TicketPurchasePlan;
import com.zewbby.smartticket.domain.entity.TicketPurchasePlanAudience;
import com.zewbby.smartticket.domain.vo.OrderRequestVO;
import com.zewbby.smartticket.enums.ShowStatusEnum;
import com.zewbby.smartticket.enums.PurchasePlanStatusEnum;
import com.zewbby.smartticket.mapper.AudiencePersonMapper;
import com.zewbby.smartticket.mapper.ShowMapper;
import com.zewbby.smartticket.mapper.TicketCategoryMapper;
import com.zewbby.smartticket.mapper.TicketPurchasePlanAudienceMapper;
import com.zewbby.smartticket.mapper.TicketPurchasePlanMapper;
import com.zewbby.smartticket.service.AsyncOrderSubmissionRejectedException;
import com.zewbby.smartticket.service.OrderService;
import com.zewbby.smartticket.service.ShowRelationCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class TicketPurchasePlanServiceImplTest {

    private TicketPurchasePlanMapper planMapper;
    private TicketPurchasePlanAudienceMapper planAudienceMapper;
    private AudiencePersonMapper audiencePersonMapper;
    private ShowMapper showMapper;
    private TicketCategoryMapper ticketCategoryMapper;
    private ShowRelationCacheService showRelationCacheService;
    private OrderService orderService;
    private PlatformTransactionManager transactionManager;
    private TicketPurchasePlanServiceImpl service;

    @BeforeEach
    void setUp() {
        planMapper = mock(TicketPurchasePlanMapper.class);
        planAudienceMapper = mock(TicketPurchasePlanAudienceMapper.class);
        audiencePersonMapper = mock(AudiencePersonMapper.class);
        showMapper = mock(ShowMapper.class);
        ticketCategoryMapper = mock(TicketCategoryMapper.class);
        showRelationCacheService = mock(ShowRelationCacheService.class);
        orderService = mock(OrderService.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        service = new TicketPurchasePlanServiceImpl(
                planMapper,
                planAudienceMapper,
                audiencePersonMapper,
                showMapper,
                ticketCategoryMapper,
                showRelationCacheService,
                orderService,
                new PurchasePlanProperties(),
                transactionManager);
        UserContext.setUserId(1L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createRejectsUnpublishedShow() {
        ShowInfo show = new ShowInfo();
        show.setId(1L);
        show.setStatus("DRAFT");
        when(showMapper.selectShowInfoById(1L)).thenReturn(show);

        assertThatThrownBy(() -> service.create(new CreatePurchasePlanRequest(1L, List.of(1L))))
                .hasMessage("演出不存在");
        verify(planMapper, never()).insert(any());
    }

    @Test
    void updateSpecRejectsQuantityAbovePlanLimit() {
        TicketPurchasePlan plan = new TicketPurchasePlan();
        plan.setId(1L);
        plan.setUserId(1L);
        plan.setStatus(PurchasePlanStatusEnum.DRAFT.getCode());
        plan.setVersion(0);
        when(planMapper.selectByIdAndUserId(1L, 1L)).thenReturn(plan);

        assertThatThrownBy(() -> service.updateSpec(
                        1L,
                        new UpdatePurchasePlanSpecRequest(1L, 2L, 5, 0)))
                .hasMessageContaining("购票数量必须在1至4张之间");
        verify(showRelationCacheService, never()).validatePublishedRelation(anyLong(), anyLong(), anyLong());
    }

    @Test
    void updateAudiencesRequiresExactFrozenQuantity() {
        TicketPurchasePlan plan = new TicketPurchasePlan();
        plan.setId(1L);
        plan.setUserId(1L);
        plan.setStatus(PurchasePlanStatusEnum.DRAFT.getCode());
        plan.setQuantity(2);
        plan.setVersion(1);
        plan.setSessionId(10L);
        when(planMapper.selectByIdAndUserId(1L, 1L)).thenReturn(plan);
        when(showMapper.selectSessionById(10L)).thenReturn(openSession());

        assertThatThrownBy(() -> service.updateAudiences(
                        1L,
                        new UpdatePurchasePlanAudienceRequest(List.of(1L), 1)))
                .hasMessage("观演人数必须与购票数量一致");
        verify(audiencePersonMapper, never()).selectByIdsAndUserId(any(), eq(1L));
    }

    @Test
    void completeRejectsAudienceCountDifferentFromQuantity() {
        TicketPurchasePlan plan = editablePlan(PurchasePlanStatusEnum.DRAFT.getCode());
        plan.setSessionId(10L);
        plan.setTicketCategoryId(20L);
        plan.setQuantity(2);
        when(planMapper.selectByIdAndUserId(1L, 1L)).thenReturn(plan);
        when(showMapper.selectSessionById(10L)).thenReturn(openSession());
        when(planAudienceMapper.selectByPlanIdAndSelectionType(1L, "SELECTED"))
                .thenReturn(List.of(planAudience(1L, 1)));

        assertThatThrownBy(() -> service.complete(1L, new CompletePurchasePlanRequest(0)))
                .hasMessage("观演人数必须与购票数量一致");
        verify(planMapper, never()).complete(anyLong(), anyLong(), anyInt(), anyInt(), any(), any());
        verifyNoOrderSubmission();
    }

    @Test
    void completeMarksPlanReadyWithoutSubmittingOrder() {
        TicketPurchasePlan before = editablePlan(PurchasePlanStatusEnum.DRAFT.getCode());
        before.setSessionId(10L);
        before.setTicketCategoryId(20L);
        before.setQuantity(2);
        TicketPurchasePlan after = editablePlan(PurchasePlanStatusEnum.READY.getCode());
        after.setSessionId(10L);
        after.setTicketCategoryId(20L);
        after.setQuantity(2);
        after.setVersion(1);
        when(planMapper.selectByIdAndUserId(1L, 1L)).thenReturn(before, after);
        when(showMapper.selectSessionById(10L)).thenReturn(openSession());
        when(planAudienceMapper.selectByPlanIdAndSelectionType(1L, "SELECTED"))
                .thenReturn(List.of(planAudience(1L, 1), planAudience(2L, 2)));
        when(planMapper.complete(eq(1L), eq(1L), eq(0), anyInt(), any(), any())).thenReturn(1);
        when(planAudienceMapper.selectByPlanIdAndSelectionType(1L, "DEFAULT"))
                .thenReturn(List.of());
        when(ticketCategoryMapper.selectOrderSnapshot(1L, 10L, 20L)).thenReturn(null);

        service.complete(1L, new CompletePurchasePlanRequest(0));

        verify(planMapper).complete(eq(1L), eq(1L), eq(0), anyInt(), any(), any());
        verifyNoOrderSubmission();
    }

    @Test
    void readyPlanCanBeEditedBeforeSaleAndReturnsToDraft() {
        TicketPurchasePlan plan = editablePlan(PurchasePlanStatusEnum.READY.getCode());
        plan.setSessionId(10L);
        plan.setTicketCategoryId(20L);
        plan.setQuantity(1);
        when(planMapper.selectByIdAndUserId(1L, 1L)).thenReturn(plan);
        when(showMapper.selectSessionById(10L)).thenReturn(openSession());
        // 关系缓存校验在被测服务中执行，测试只验证版本更新契约。
        when(planMapper.updateSpec(eq(1L), eq(1L), eq(10L), eq(20L), eq(1), any(), eq(0), any(), any()))
                .thenReturn(1);
        when(planMapper.selectByIdAndUserId(1L, 1L)).thenReturn(plan);

        service.updateSpec(1L, new UpdatePurchasePlanSpecRequest(10L, 20L, 1, 0));

        verify(planMapper).updateSpec(eq(1L), eq(1L), eq(10L), eq(20L), eq(1), any(), eq(0), any(), any());
    }

    @Test
    void synchronousOrderRejectionMarksPlanFailedInsteadOfLeavingItUnreconcilable() {
        TicketPurchasePlan plan = submittablePlan(PurchasePlanStatusEnum.READY.getCode());
        when(planMapper.selectByIdAndUserId(1L, 1L)).thenReturn(plan);
        when(showMapper.selectSessionById(10L)).thenReturn(saleWindowOpenSession());
        when(planAudienceMapper.selectByPlanIdAndSelectionType(1L, "SELECTED"))
                .thenReturn(List.of(planAudience(101L, 1)));
        when(planMapper.beginSubmitting(eq(1L), eq(1L), eq(4), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(orderService.submitAsyncOrder(any()))
                .thenThrow(new AsyncOrderSubmissionRejectedException("重复提交"));
        when(planMapper.markFailed(eq(1L), any(), eq(PurchasePlanStatusEnum.FAILED.getCode()),
                eq("重复提交"), any())).thenReturn(1);

        assertThatThrownBy(() -> service.submit(1L, new SubmitPurchasePlanRequest(4, "token-1", null)))
                .isInstanceOf(com.zewbby.smartticket.common.BusinessException.class)
                .hasMessage("重复提交");

        verify(planMapper).markFailed(eq(1L), any(), eq(PurchasePlanStatusEnum.FAILED.getCode()),
                eq("重复提交"), any());
        verify(planMapper, never()).markReconciliationRequired(anyLong(), any(), any(), any(), any());
        verifySubmissionTransactions();
    }

    @Test
    void unknownOrderSubmissionOutcomeRemainsInReconciliation() {
        TicketPurchasePlan plan = submittablePlan(PurchasePlanStatusEnum.READY.getCode());
        when(planMapper.selectByIdAndUserId(1L, 1L)).thenReturn(plan);
        when(showMapper.selectSessionById(10L)).thenReturn(saleWindowOpenSession());
        when(planAudienceMapper.selectByPlanIdAndSelectionType(1L, "SELECTED"))
                .thenReturn(List.of(planAudience(101L, 1)));
        when(planMapper.beginSubmitting(eq(1L), eq(1L), eq(4), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(orderService.submitAsyncOrder(any())).thenThrow(new IllegalStateException("投递结果未知"));
        when(planMapper.markReconciliationRequired(eq(1L), any(),
                eq(PurchasePlanStatusEnum.RECONCILIATION_REQUIRED.getCode()), any(), any())).thenReturn(1);

        assertThatThrownBy(() -> service.submit(1L, new SubmitPurchasePlanRequest(4, "token-2", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("投递结果未知");

        verify(planMapper).markReconciliationRequired(eq(1L), any(),
                eq(PurchasePlanStatusEnum.RECONCILIATION_REQUIRED.getCode()), any(), any());
        verify(planMapper, never()).markFailed(anyLong(), any(), any(), any(), any());
        verifySubmissionTransactions();
    }

    @Test
    void sameTokenReturnsPreboundRequestIdBeforeRequestRowExists() {
        TicketPurchasePlan plan = submittablePlan(PurchasePlanStatusEnum.SUBMITTING.getCode());
        plan.setOrderRequestId("PLANREQ-1");
        plan.setIdempotencyToken("same-token");
        plan.setSubmittedAt(LocalDateTime.now());
        when(planMapper.selectByIdAndUserId(1L, 1L)).thenReturn(plan);

        OrderRequestVO result = service.submit(1L, new SubmitPurchasePlanRequest(4, "same-token", null));

        assertThat(result.getRequestId()).isEqualTo("PLANREQ-1");
        assertThat(result.getStatus()).isEqualTo(PurchasePlanStatusEnum.SUBMITTING.getCode());
        verify(orderService, never()).getOrderRequestResult(any());
        verify(planMapper, never()).beginSubmitting(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void unknownInnerTransactionRollbackDoesNotUndoPlanReconciliation() {
        RecordingTransactionManager manager = new RecordingTransactionManager();
        service = new TicketPurchasePlanServiceImpl(planMapper, planAudienceMapper, audiencePersonMapper,
                showMapper, ticketCategoryMapper, showRelationCacheService, orderService,
                new PurchasePlanProperties(), manager);
        TicketPurchasePlan plan = submittablePlan(PurchasePlanStatusEnum.READY.getCode());
        when(planMapper.selectByIdAndUserId(1L, 1L)).thenReturn(plan);
        when(showMapper.selectSessionById(10L)).thenReturn(saleWindowOpenSession());
        when(planAudienceMapper.selectByPlanIdAndSelectionType(1L, "SELECTED"))
                .thenReturn(List.of(planAudience(101L, 1)));
        when(planMapper.beginSubmitting(eq(1L), eq(1L), eq(4), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(planMapper.markReconciliationRequired(eq(1L), any(), any(), any(), any())).thenReturn(1);
        when(orderService.submitAsyncOrder(any())).thenAnswer(invocation ->
                new TransactionTemplate(manager).execute(status -> {
                    throw new IllegalStateException("订单事务回滚");
                }));

        assertThatThrownBy(() -> service.submit(1L, new SubmitPurchasePlanRequest(4, "token-2", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("订单事务回滚");

        assertThat(manager.events).containsExactly("begin", "commit", "begin", "rollback", "begin", "commit");
        verify(planMapper).markReconciliationRequired(eq(1L), any(),
                eq(PurchasePlanStatusEnum.RECONCILIATION_REQUIRED.getCode()), any(), any());
    }

    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);
        private final List<String> events = new ArrayList<>();

        @Override
        protected Object doGetTransaction() {
            return active.get();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return Boolean.TRUE.equals(transaction);
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            active.set(true);
            events.add("begin");
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            events.add("commit");
            active.remove();
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            events.add("rollback");
            active.remove();
        }
    }

    private void verifySubmissionTransactions() {
        ArgumentCaptor<TransactionDefinition> definitions = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager, times(3)).getTransaction(definitions.capture());
        assertThat(definitions.getAllValues())
                .extracting(TransactionDefinition::getPropagationBehavior)
                .containsExactly(TransactionDefinition.PROPAGATION_NOT_SUPPORTED,
                        TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        verify(transactionManager, times(2)).commit(any());
        verify(transactionManager).rollback(any());
    }

    private TicketPurchasePlan editablePlan(String status) {
        TicketPurchasePlan plan = new TicketPurchasePlan();
        plan.setId(1L);
        plan.setUserId(1L);
        plan.setShowId(1L);
        plan.setStatus(status);
        plan.setVersion(0);
        plan.setDefaultAudienceCount(2);
        plan.setSelectedAudienceCount(2);
        return plan;
    }

    private TicketPurchasePlan submittablePlan(String status) {
        TicketPurchasePlan plan = editablePlan(status);
        plan.setSessionId(10L);
        plan.setTicketCategoryId(20L);
        plan.setQuantity(1);
        plan.setVersion(4);
        plan.setCompletedVersion(3);
        return plan;
    }

    private PerformanceSession saleWindowOpenSession() {
        PerformanceSession session = openSession();
        session.setSaleStartTime(LocalDateTime.now().minusMinutes(1));
        session.setSaleEndTime(LocalDateTime.now().plusMinutes(30));
        return session;
    }

    private PerformanceSession openSession() {
        PerformanceSession session = new PerformanceSession();
        session.setId(10L);
        session.setShowId(1L);
        session.setStatus(ShowStatusEnum.PUBLISHED.getCode());
        session.setStartTime(LocalDateTime.now().plusDays(1));
        session.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        session.setSaleStartTime(LocalDateTime.now().plusHours(1));
        session.setSaleEndTime(LocalDateTime.now().plusHours(12));
        return session;
    }

    private TicketPurchasePlanAudience planAudience(Long audienceId, int lineNo) {
        return new TicketPurchasePlanAudience(1L, 1L, audienceId, "SELECTED", lineNo,
                "观演人" + audienceId, "hash" + audienceId, LocalDateTime.now());
    }

    private void verifyNoOrderSubmission() {
        verify(orderService, never()).submitAsyncOrder(any());
    }
}

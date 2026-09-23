package com.zewbby.smartticket.service.impl;

import com.zewbby.smartticket.config.PurchasePlanProperties;
import com.zewbby.smartticket.domain.entity.TicketOrderRequest;
import com.zewbby.smartticket.domain.entity.TicketPurchasePlan;
import com.zewbby.smartticket.enums.PurchasePlanStatusEnum;
import com.zewbby.smartticket.mapper.OrderRequestMapper;
import com.zewbby.smartticket.mapper.TicketPurchasePlanMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PurchasePlanReconciliationServiceImplTest {

    private TicketPurchasePlanMapper planMapper;
    private OrderRequestMapper orderRequestMapper;
    private PurchasePlanReconciliationServiceImpl service;

    @BeforeEach
    void setUp() {
        planMapper = mock(TicketPurchasePlanMapper.class);
        orderRequestMapper = mock(OrderRequestMapper.class);
        PurchasePlanProperties properties = new PurchasePlanProperties();
        service = new PurchasePlanReconciliationServiceImpl(planMapper, orderRequestMapper, properties);
    }

    @Test
    void marksOrderCreatedWhenStaleRequestSucceededWithOrderId() {
        TicketPurchasePlan plan = plan(11L, "REQ-11");
        TicketOrderRequest request = request("REQ-11", "SUCCESS", 99L, true, false, "NONE");
        when(planMapper.selectStaleForReconciliation(any(), anyInt())).thenReturn(List.of(plan));
        when(orderRequestMapper.selectByRequestId("REQ-11")).thenReturn(request);

        service.reconcileStalePlans();

        verify(planMapper).markOrderCreated(eq(11L), eq("REQ-11"), eq(99L),
                eq(PurchasePlanStatusEnum.ORDER_CREATED.getCode()), any());
        verify(planMapper, never()).markFailed(any(), any(), any(), any(), any());
    }

    @Test
    void marksFailedOnlyAfterRedisDeductionIsKnownSafe() {
        TicketPurchasePlan plan = plan(12L, "REQ-12");
        TicketOrderRequest request = request("REQ-12", "FAILED", null, true, true, "COMPENSATED");
        when(planMapper.selectStaleForReconciliation(any(), anyInt())).thenReturn(List.of(plan));
        when(orderRequestMapper.selectByRequestId("REQ-12")).thenReturn(request);

        service.reconcileStalePlans();

        verify(planMapper).markFailed(eq(12L), eq("REQ-12"),
                eq(PurchasePlanStatusEnum.FAILED.getCode()), anyString(), any());
        verify(planMapper, never()).markOrderCreated(any(), any(), any(), any(), any());
    }

    @Test
    void acceptsCompensatedRequestWhenCompensationStatusConfirmsRelease() {
        TicketPurchasePlan plan = plan(17L, "REQ-17");
        TicketOrderRequest request = request("REQ-17", "COMPENSATED", null, true, false, "COMPENSATED");
        when(planMapper.selectStaleForReconciliation(any(), anyInt())).thenReturn(List.of(plan));
        when(orderRequestMapper.selectByRequestId("REQ-17")).thenReturn(request);

        service.reconcileStalePlans();

        verify(planMapper).markFailed(eq(17L), eq("REQ-17"),
                eq(PurchasePlanStatusEnum.FAILED.getCode()), anyString(), any());
    }

    @Test
    void acceptsFailedRequestWhenRedisWasNeverDeducted() {
        TicketPurchasePlan plan = plan(18L, "REQ-18");
        TicketOrderRequest request = request("REQ-18", "FAILED", null, false, false, "NONE");
        when(planMapper.selectStaleForReconciliation(any(), anyInt())).thenReturn(List.of(plan));
        when(orderRequestMapper.selectByRequestId("REQ-18")).thenReturn(request);

        service.reconcileStalePlans();

        verify(planMapper).markFailed(eq(18L), eq("REQ-18"),
                eq(PurchasePlanStatusEnum.FAILED.getCode()), anyString(), any());
    }

    @Test
    void keepsFailedRequestInReconciliationWhenRedisCompensationIsUncertain() {
        TicketPurchasePlan plan = plan(13L, "REQ-13");
        TicketOrderRequest request = request("REQ-13", "FAILED", null, true, false, "COMPENSATE_FAILED");
        when(planMapper.selectStaleForReconciliation(any(), anyInt())).thenReturn(List.of(plan));
        when(orderRequestMapper.selectByRequestId("REQ-13")).thenReturn(request);

        service.reconcileStalePlans();

        verify(planMapper).markReconciliationRequired(eq(13L), eq("REQ-13"),
                eq(PurchasePlanStatusEnum.RECONCILIATION_REQUIRED.getCode()), anyString(), any());
        verify(planMapper, never()).markFailed(any(), any(), any(), any(), any());
    }

    @Test
    void leavesQueuedRequestUnchangedForLaterRetry() {
        TicketPurchasePlan plan = plan(14L, "REQ-14");
        TicketOrderRequest request = request("REQ-14", "QUEUED", null, true, false, "NONE");
        when(planMapper.selectStaleForReconciliation(any(), anyInt())).thenReturn(List.of(plan));
        when(orderRequestMapper.selectByRequestId("REQ-14")).thenReturn(request);

        service.reconcileStalePlans();

        verify(orderRequestMapper).selectByRequestId("REQ-14");
        verify(planMapper, never()).markOrderCreated(any(), any(), any(), any(), any());
        verify(planMapper, never()).markFailed(any(), any(), any(), any(), any());
        verify(planMapper, never()).markReconciliationRequired(any(), any(), any(), any(), any());
    }

    @Test
    void leavesAllKnownInFlightRequestStatesForLaterRetry() {
        TicketPurchasePlan queued = plan(19L, "REQ-19");
        TicketPurchasePlan preDeducted = plan(20L, "REQ-20");
        TicketPurchasePlan processing = plan(21L, "REQ-21");
        when(planMapper.selectStaleForReconciliation(any(), anyInt()))
                .thenReturn(List.of(queued, preDeducted, processing));
        when(orderRequestMapper.selectByRequestId("REQ-19"))
                .thenReturn(request("REQ-19", "QUEUED", null, true, false, "NONE"));
        when(orderRequestMapper.selectByRequestId("REQ-20"))
                .thenReturn(request("REQ-20", "PRE_DEDUCTED", null, true, false, "NONE"));
        when(orderRequestMapper.selectByRequestId("REQ-21"))
                .thenReturn(request("REQ-21", "PROCESSING", null, true, false, "NONE"));

        service.reconcileStalePlans();

        verify(planMapper, never()).markOrderCreated(any(), any(), any(), any(), any());
        verify(planMapper, never()).markFailed(any(), any(), any(), any(), any());
        verify(planMapper, never()).markReconciliationRequired(any(), any(), any(), any(), any());
    }

    @Test
    void retainsReconciliationWhenOrderRequestIsMissing() {
        TicketPurchasePlan plan = plan(15L, "REQ-15");
        when(planMapper.selectStaleForReconciliation(any(), anyInt())).thenReturn(List.of(plan));

        service.reconcileStalePlans();

        verify(planMapper).markReconciliationRequired(eq(15L), eq("REQ-15"),
                eq(PurchasePlanStatusEnum.RECONCILIATION_REQUIRED.getCode()), anyString(), any());
        verify(planMapper, never()).markFailed(any(), any(), any(), any(), any());
    }

    @Test
    void doesNotQueryOrderRequestWhenPlanHasNoRequestId() {
        TicketPurchasePlan plan = plan(16L, null);
        when(planMapper.selectStaleForReconciliation(any(), anyInt())).thenReturn(List.of(plan));

        service.reconcileStalePlans();

        verify(orderRequestMapper, never()).selectByRequestId(any());
        verifyNoMoreInteractions(orderRequestMapper);
    }

    @Test
    void doesNotScanWhenReconciliationIsDisabled() {
        PurchasePlanProperties properties = new PurchasePlanProperties();
        properties.setReconciliationEnabled(false);
        service = new PurchasePlanReconciliationServiceImpl(planMapper, orderRequestMapper, properties);

        service.reconcileStalePlans();

        verify(planMapper, never()).selectStaleForReconciliation(any(), anyInt());
    }

    private TicketPurchasePlan plan(Long id, String requestId) {
        TicketPurchasePlan plan = new TicketPurchasePlan();
        plan.setId(id);
        plan.setOrderRequestId(requestId);
        plan.setStatus(PurchasePlanStatusEnum.RECONCILIATION_REQUIRED.getCode());
        return plan;
    }

    private TicketOrderRequest request(String requestId,
                                       String status,
                                       Long orderId,
                                       boolean redisDeducted,
                                       boolean compensated,
                                       String compensationStatus) {
        TicketOrderRequest request = new TicketOrderRequest();
        request.setRequestId(requestId);
        request.setStatus(status);
        request.setOrderId(orderId);
        request.setRedisDeducted(redisDeducted);
        request.setCompensated(compensated);
        request.setCompensationStatus(compensationStatus);
        return request;
    }
}

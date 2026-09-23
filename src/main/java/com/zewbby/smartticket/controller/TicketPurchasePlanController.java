package com.zewbby.smartticket.controller;

import com.zewbby.smartticket.common.ApiResponse;
import com.zewbby.smartticket.domain.dto.ConfirmPurchasePlanRequest;
import com.zewbby.smartticket.domain.dto.CompletePurchasePlanRequest;
import com.zewbby.smartticket.domain.dto.CreatePurchasePlanRequest;
import com.zewbby.smartticket.domain.dto.UpdatePurchasePlanSpecRequest;
import com.zewbby.smartticket.domain.dto.UpdatePurchasePlanAudienceRequest;
import com.zewbby.smartticket.domain.dto.SubmitPurchasePlanRequest;
import com.zewbby.smartticket.domain.vo.OrderRequestVO;
import com.zewbby.smartticket.domain.vo.TicketPurchasePlanVO;
import com.zewbby.smartticket.service.TicketPurchasePlanService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-plans")
public class TicketPurchasePlanController {

    private final TicketPurchasePlanService ticketPurchasePlanService;

    public TicketPurchasePlanController(TicketPurchasePlanService ticketPurchasePlanService) {
        this.ticketPurchasePlanService = ticketPurchasePlanService;
    }

    @PostMapping
    public ApiResponse<TicketPurchasePlanVO> create(@Valid @RequestBody CreatePurchasePlanRequest request) {
        return ApiResponse.success(ticketPurchasePlanService.create(request));
    }

    @GetMapping("/{planId}")
    public ApiResponse<TicketPurchasePlanVO> get(@PathVariable Long planId) {
        return ApiResponse.success(ticketPurchasePlanService.get(planId));
    }

    @GetMapping
    public ApiResponse<List<TicketPurchasePlanVO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        return ApiResponse.success(ticketPurchasePlanService.list(status, limit));
    }

    @PutMapping("/{planId}/spec")
    public ApiResponse<TicketPurchasePlanVO> updateSpec(
            @PathVariable Long planId,
            @Valid @RequestBody UpdatePurchasePlanSpecRequest request) {
        return ApiResponse.success(ticketPurchasePlanService.updateSpec(planId, request));
    }

    @PostMapping("/{planId}/confirm-spec")
    @Deprecated
    public ApiResponse<TicketPurchasePlanVO> confirmSpec(
            @PathVariable Long planId,
            @Valid @RequestBody ConfirmPurchasePlanRequest request) {
        return ApiResponse.success(ticketPurchasePlanService.confirmSpec(planId, request));
    }

    @PostMapping("/{planId}/complete")
    public ApiResponse<TicketPurchasePlanVO> complete(
            @PathVariable Long planId,
            @Valid @RequestBody CompletePurchasePlanRequest request) {
        return ApiResponse.success(ticketPurchasePlanService.complete(planId, request));
    }

    @PutMapping("/{planId}/audiences")
    public ApiResponse<TicketPurchasePlanVO> updateAudiences(
            @PathVariable Long planId,
            @Valid @RequestBody UpdatePurchasePlanAudienceRequest request) {
        return ApiResponse.success(ticketPurchasePlanService.updateAudiences(planId, request));
    }

    @PostMapping("/{planId}/submit")
    public ApiResponse<OrderRequestVO> submit(
            @PathVariable Long planId,
            @Valid @RequestBody SubmitPurchasePlanRequest request) {
        return ApiResponse.success(ticketPurchasePlanService.submit(planId, request));
    }

    @PostMapping("/{planId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long planId,
                                    @Valid @RequestBody ConfirmPurchasePlanRequest request) {
        ticketPurchasePlanService.cancel(planId, request.getVersion());
        return ApiResponse.success(null);
    }
}

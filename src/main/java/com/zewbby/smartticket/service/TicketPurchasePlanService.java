package com.zewbby.smartticket.service;

import com.zewbby.smartticket.domain.dto.CompletePurchasePlanRequest;
import com.zewbby.smartticket.domain.dto.ConfirmPurchasePlanRequest;
import com.zewbby.smartticket.domain.dto.CreatePurchasePlanRequest;
import com.zewbby.smartticket.domain.dto.SubmitPurchasePlanRequest;
import com.zewbby.smartticket.domain.dto.UpdatePurchasePlanAudienceRequest;
import com.zewbby.smartticket.domain.dto.UpdatePurchasePlanSpecRequest;
import com.zewbby.smartticket.domain.vo.OrderRequestVO;
import com.zewbby.smartticket.domain.vo.TicketPurchasePlanVO;

import java.util.List;

public interface TicketPurchasePlanService {

    TicketPurchasePlanVO create(CreatePurchasePlanRequest request);

    TicketPurchasePlanVO get(Long planId);

    List<TicketPurchasePlanVO> list(String status, Integer limit);

    TicketPurchasePlanVO updateSpec(Long planId, UpdatePurchasePlanSpecRequest request);

    TicketPurchasePlanVO complete(Long planId, CompletePurchasePlanRequest request);

    /** 兼容旧客户端，内部与 complete 使用同一语义。 */
    TicketPurchasePlanVO confirmSpec(Long planId, ConfirmPurchasePlanRequest request);

    TicketPurchasePlanVO updateAudiences(Long planId, UpdatePurchasePlanAudienceRequest request);

    OrderRequestVO submit(Long planId, SubmitPurchasePlanRequest request);

    void cancel(Long planId, Integer version);
}

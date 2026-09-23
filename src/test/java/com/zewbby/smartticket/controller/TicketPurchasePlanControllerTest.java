package com.zewbby.smartticket.controller;

import com.zewbby.smartticket.domain.dto.SubmitPurchasePlanRequest;
import com.zewbby.smartticket.domain.vo.OrderRequestVO;
import com.zewbby.smartticket.service.TicketPurchasePlanService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketPurchasePlanControllerTest {

    private final TicketPurchasePlanService service = mock(TicketPurchasePlanService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TicketPurchasePlanController(service)).build();

    @Test
    void submitUsesTheActualRetryEntryPoint() throws Exception {
        OrderRequestVO result = new OrderRequestVO();
        result.setRequestId("PLANREQ-1");
        when(service.submit(eq(1L), eq(new SubmitPurchasePlanRequest(4, "new-token", null))))
                .thenReturn(result);

        mockMvc.perform(post("/api/purchase-plans/1/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":4,\"idempotencyToken\":\"new-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value("PLANREQ-1"));

        verify(service).submit(1L, new SubmitPurchasePlanRequest(4, "new-token", null));
    }

    @Test
    void obsoleteRetryRouteIsAbsent() throws Exception {
        mockMvc.perform(post("/api/purchase-plans/1/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":4}"))
                .andExpect(status().isNotFound());
    }
}

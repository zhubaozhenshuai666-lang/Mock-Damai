package com.zewbby.smartticket.task;

import com.zewbby.smartticket.config.PurchasePlanProperties;
import com.zewbby.smartticket.enums.PurchasePlanStatusEnum;
import com.zewbby.smartticket.mapper.TicketPurchasePlanMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PurchasePlanExpiryScanTaskTest {

    @Test
    void expiresDraftAndReadyPlansAgainstSaleWindow() {
        TicketPurchasePlanMapper mapper = mock(TicketPurchasePlanMapper.class);
        PurchasePlanProperties properties = new PurchasePlanProperties();
        PurchasePlanExpiryScanTask task = new PurchasePlanExpiryScanTask(mapper, properties);

        task.expirePlans();

        verify(mapper).expireEditable(any(),
                org.mockito.ArgumentMatchers.eq(PurchasePlanStatusEnum.EXPIRED.getCode()),
                any(), anyInt(), any());
        verify(mapper).expireReadyAfterSale(any(),
                org.mockito.ArgumentMatchers.eq(PurchasePlanStatusEnum.EXPIRED.getCode()),
                any(), anyInt(), any());
    }
}

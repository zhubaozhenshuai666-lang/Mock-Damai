package com.zewbby.smartticket.task;

import com.zewbby.smartticket.config.PurchasePlanProperties;
import com.zewbby.smartticket.service.PurchasePlanReconciliationService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PurchasePlanReconciliationScanTaskTest {

    @Test
    void invokesReconciliationServiceOnScheduledScan() {
        PurchasePlanProperties properties = new PurchasePlanProperties();
        PurchasePlanReconciliationService service = mock(PurchasePlanReconciliationService.class);
        PurchasePlanReconciliationScanTask task = new PurchasePlanReconciliationScanTask(service, properties);

        task.scanStalePlans();

        verify(service).reconcileStalePlans();
    }

    @Test
    void skipsReconciliationWhenFeatureIsDisabled() {
        PurchasePlanProperties properties = new PurchasePlanProperties();
        properties.setReconciliationEnabled(false);
        PurchasePlanReconciliationService service = mock(PurchasePlanReconciliationService.class);
        PurchasePlanReconciliationScanTask task = new PurchasePlanReconciliationScanTask(service, properties);

        task.scanStalePlans();

        verify(service, never()).reconcileStalePlans();
    }
}

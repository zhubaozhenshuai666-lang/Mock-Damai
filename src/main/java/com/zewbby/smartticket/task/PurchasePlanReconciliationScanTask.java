package com.zewbby.smartticket.task;

import com.zewbby.smartticket.config.PurchasePlanProperties;
import com.zewbby.smartticket.service.PurchasePlanReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PurchasePlanReconciliationScanTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(PurchasePlanReconciliationScanTask.class);

    private final PurchasePlanReconciliationService reconciliationService;

    private final PurchasePlanProperties properties;

    public PurchasePlanReconciliationScanTask(PurchasePlanReconciliationService reconciliationService,
                                               PurchasePlanProperties properties) {
        this.reconciliationService = reconciliationService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "#{@purchasePlanProperties.safeReconciliationFixedDelayMillis()}")
    public void scanStalePlans() {
        if (!properties.isReconciliationEnabled()) {
            return;
        }
        try {
            reconciliationService.reconcileStalePlans();
        } catch (RuntimeException exception) {
            LOGGER.error("Purchase plan reconciliation scan failed", exception);
        }
    }
}

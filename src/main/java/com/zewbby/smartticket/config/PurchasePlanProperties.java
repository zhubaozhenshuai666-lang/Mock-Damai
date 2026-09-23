package com.zewbby.smartticket.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "smart-ticket.purchase-plan")
public class PurchasePlanProperties {

    private boolean enabled = true;

    private int maxQuantity = 4;

    private int expireScanBatchSize = 500;

    private long expireScanFixedDelayMillis = 1000L;

    private boolean reconciliationEnabled = true;

    private int reconciliationBatchSize = 500;

    private long reconciliationFixedDelayMillis = 1000L;

    private long reconciliationTimeoutMillis = 120_000L;


    public int safeMaxQuantity() {
        return Math.max(1, maxQuantity);
    }

    public int safeExpireScanBatchSize() {
        return Math.max(1, expireScanBatchSize);
    }

    public long safeExpireScanFixedDelayMillis() {
        return Math.max(100L, expireScanFixedDelayMillis);
    }

    public int safeReconciliationBatchSize() {
        return Math.max(1, reconciliationBatchSize);
    }

    public long safeReconciliationFixedDelayMillis() {
        return Math.max(100L, reconciliationFixedDelayMillis);
    }

    public long safeReconciliationTimeoutMillis() {
        return Math.max(1000L, reconciliationTimeoutMillis);
    }

}

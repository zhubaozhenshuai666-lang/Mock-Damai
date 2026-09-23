package com.zewbby.smartticket.task;

import com.zewbby.smartticket.config.PurchasePlanProperties;
import com.zewbby.smartticket.enums.PurchasePlanStatusEnum;
import com.zewbby.smartticket.mapper.TicketPurchasePlanMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 按场次开售窗口清理已失去预约资格的计划。 */
@Component
public class PurchasePlanExpiryScanTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(PurchasePlanExpiryScanTask.class);

    private final TicketPurchasePlanMapper planMapper;

    private final PurchasePlanProperties properties;

    public PurchasePlanExpiryScanTask(TicketPurchasePlanMapper planMapper,
                                      PurchasePlanProperties properties) {
        this.planMapper = planMapper;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "#{@purchasePlanProperties.safeExpireScanFixedDelayMillis()}")
    public void expirePlans() {
        if (!properties.isEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int batchSize = properties.safeExpireScanBatchSize();
        try {
            int rows = planMapper.expireEditable(
                    now,
                    PurchasePlanStatusEnum.EXPIRED.getCode(),
                    "预约计划已错过开售时间",
                    batchSize,
                    now);
            if (rows > 0) {
                LOGGER.info("Expired editable purchase plans after sale start, count={}", rows);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to expire editable purchase plans", exception);
        }
        try {
            int rows = planMapper.expireReadyAfterSale(
                    now,
                    PurchasePlanStatusEnum.EXPIRED.getCode(),
                    "预约计划已超过售票结束时间",
                    batchSize,
                    now);
            if (rows > 0) {
                LOGGER.info("Expired ready purchase plans after sale end, count={}", rows);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to expire ready purchase plans", exception);
        }
    }
}

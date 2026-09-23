package com.zewbby.smartticket.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "smart-ticket.artist-ranking")
public class ArtistRankingProperties {

    private boolean enabled = true;

    private double detailClickWeight = 1.0D;

    private double searchWeight = 0.3D;

    private double purchaseIntentWeight = 3.0D;

    private double paidOrderWeight = 10.0D;

    private double contentComponentWeight = 0.40D;

    private double interestComponentWeight = 0.25D;

    private double intentComponentWeight = 0.15D;

    private double paidComponentWeight = 0.12D;

    private double taskComponentWeight = 0.08D;

    /** 未登录请求只能作为低置信度兴趣信号，避免共享出口 IP 直接刷榜。 */
    private double anonymousQualityFactor = 0.35D;

    private double authenticatedQualityFactor = 1.0D;

    /** 对同一艺人采用对数边际增量，避免高基数艺人无限线性碾压。 */
    private double saturationScale = 100.0D;

    private int defaultLimit = 20;

    private int maxLimit = 100;

    private int dedupTtlSeconds = 86400;

    private int dailyKeyTtlSeconds = 691200;

    private int weeklyKeyTtlSeconds = 1296000;

    private int monthlyKeyTtlSeconds = 5184000;

    /** 热榜按最近小时桶聚合，默认保留 48 小时，覆盖跨日场景。 */
    private int hourlyKeyTtlSeconds = 172800;

    private int hotWindowHours = 24;

    /** 热榜指数衰减半衰期，单位小时。 */
    private double hotHalfLifeHours = 6.0D;

    private int artistMaxLength = 64;
}

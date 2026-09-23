package com.zewbby.smartticket.enums;

/**
 * 排行榜的可解释信号维度。各维度独立落 ZSET，查询时再按业务权重聚合。
 */
public enum ArtistRankingComponentEnum {

    CONTENT("content"),
    INTEREST("interest"),
    INTENT("intent"),
    PAID("paid"),
    TASK("task");

    private final String code;

    ArtistRankingComponentEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

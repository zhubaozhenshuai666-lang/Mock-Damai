package com.zewbby.smartticket.enums;

public enum ArtistRankingPeriodEnum {

    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    HOT("hot"),
    ALL("all");

    private final String code;

    ArtistRankingPeriodEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ArtistRankingPeriodEnum from(String value) {
        if (value == null || value.isBlank()) {
            return WEEKLY;
        }
        for (ArtistRankingPeriodEnum item : values()) {
            if (item.code.equalsIgnoreCase(value.trim()) || item.name().equalsIgnoreCase(value.trim())) {
                return item;
            }
        }
        throw new IllegalArgumentException("榜单周期仅支持 hot、daily、weekly、monthly 或 all");
    }
}

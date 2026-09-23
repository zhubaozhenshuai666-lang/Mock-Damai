package com.zewbby.smartticket.enums;

public enum AudienceStatusEnum {

    ACTIVE("ACTIVE", "可用"),
    DISABLED("DISABLED", "已停用");

    private final String code;

    private final String description;

    AudienceStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}

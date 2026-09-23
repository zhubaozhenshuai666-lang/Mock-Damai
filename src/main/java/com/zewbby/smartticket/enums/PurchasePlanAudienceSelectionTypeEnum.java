package com.zewbby.smartticket.enums;

public enum PurchasePlanAudienceSelectionTypeEnum {

    DEFAULT("DEFAULT", "预约预填"),
    SELECTED("SELECTED", "提交时选择");

    private final String code;

    private final String description;

    PurchasePlanAudienceSelectionTypeEnum(String code, String description) {
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

package com.zewbby.smartticket.enums;

public enum PurchasePlanStatusEnum {

    DRAFT("DRAFT", "草稿"),
    READY("READY", "预约已完成"),
    SUBMITTING("SUBMITTING", "正在提交抢票"),
    RECONCILIATION_REQUIRED("RECONCILIATION_REQUIRED", "等待对账"),
    ORDER_CREATED("ORDER_CREATED", "已创建订单"),
    FAILED("FAILED", "抢票失败"),
    EXPIRED("EXPIRED", "已过期"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;

    private final String description;

    PurchasePlanStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static boolean isEditable(String status) {
        return DRAFT.code.equals(status) || READY.code.equals(status);
    }

    public static boolean isSubmittable(String status) {
        return READY.code.equals(status) || FAILED.code.equals(status);
    }
}

package com.zewbby.smartticket.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePurchasePlanSpecRequest {

    @NotNull(message = "场次ID不能为空")
    private Long sessionId;

    @NotNull(message = "票档ID不能为空")
    private Long ticketCategoryId;

    @NotNull(message = "购票数量不能为空")
    @Positive(message = "购票数量必须大于0")
    private Integer quantity;

    @NotNull(message = "计划版本不能为空")
    private Integer version;
}

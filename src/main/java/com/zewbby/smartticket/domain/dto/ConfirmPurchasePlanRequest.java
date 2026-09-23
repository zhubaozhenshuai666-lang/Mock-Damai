package com.zewbby.smartticket.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmPurchasePlanRequest {

    @NotNull(message = "计划版本不能为空")
    private Integer version;
}

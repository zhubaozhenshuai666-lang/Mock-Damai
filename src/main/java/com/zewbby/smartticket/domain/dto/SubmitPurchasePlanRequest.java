package com.zewbby.smartticket.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitPurchasePlanRequest {

    @NotNull(message = "计划版本不能为空")
    private Integer version;

    @NotBlank(message = "幂等token不能为空")
    private String idempotencyToken;

    private String admissionToken;
}

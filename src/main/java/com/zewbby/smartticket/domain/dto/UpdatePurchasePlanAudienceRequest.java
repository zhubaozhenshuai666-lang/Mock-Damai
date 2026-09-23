package com.zewbby.smartticket.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePurchasePlanAudienceRequest {

    @NotEmpty(message = "请选择实名观演人")
    private List<Long> audienceIds;

    @NotNull(message = "计划版本不能为空")
    private Integer version;
}

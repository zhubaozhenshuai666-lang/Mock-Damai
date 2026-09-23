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
public class CreatePurchasePlanRequest {

    @NotNull(message = "演出ID不能为空")
    private Long showId;

    @NotEmpty(message = "请至少选择一名实名观演人")
    private List<Long> defaultAudienceIds;
}

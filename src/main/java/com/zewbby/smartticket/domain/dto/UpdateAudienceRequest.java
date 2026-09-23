package com.zewbby.smartticket.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAudienceRequest {

    @NotBlank(message = "观演人姓名不能为空")
    private String name;

    private String idType;

    @NotBlank(message = "观演人证件号不能为空")
    private String idNo;

    @NotNull(message = "观演人版本不能为空")
    private Integer version;
}

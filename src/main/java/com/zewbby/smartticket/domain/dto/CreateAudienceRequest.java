package com.zewbby.smartticket.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAudienceRequest {

    @NotBlank(message = "观演人姓名不能为空")
    private String name;

    private String idType;

    @NotBlank(message = "观演人证件号不能为空")
    private String idNo;
}

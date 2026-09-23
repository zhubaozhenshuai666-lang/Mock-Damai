package com.zewbby.smartticket.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminCreateSessionRequest {

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    @NotNull(message = "开售开始时间不能为空")
    private LocalDateTime saleStartTime;

    @NotNull(message = "开售结束时间不能为空")
    private LocalDateTime saleEndTime;
}

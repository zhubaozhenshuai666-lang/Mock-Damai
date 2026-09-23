package com.zewbby.smartticket.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AudiencePersonVO {

    private Long id;

    private Long userId;

    private String name;

    private String idType;

    private String maskedIdNo;

    private String status;

    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

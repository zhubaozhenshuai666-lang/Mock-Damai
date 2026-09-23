package com.zewbby.smartticket.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketPurchasePlanVO {

    private Long id;

    private String planNo;

    private Long userId;

    private Long showId;

    private Long sessionId;

    private Long ticketCategoryId;

    private Integer quantity;

    private String status;

    private Integer version;

    private Integer completedVersion;

    private Integer defaultAudienceCount;

    private Integer selectedAudienceCount;

    private List<Long> defaultAudienceIds;

    private List<Long> selectedAudienceIds;

    private BigDecimal ticketPrice;

    private BigDecimal totalAmount;

    private LocalDateTime expireTime;

    private LocalDateTime confirmedAt;

    private LocalDateTime completedAt;

    private LocalDateTime submittedAt;

    private String orderRequestId;

    private Long orderId;

    private String failReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

package com.zewbby.smartticket.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("ticket_purchase_plan")
public class TicketPurchasePlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String planNo;

    private Long userId;

    private Long showId;

    private Long sessionId;

    private Long ticketCategoryId;

    private Integer quantity;

    private String status;

    private Integer version;

    private String activePlanKey;

    private Integer completedVersion;

    private Integer defaultAudienceCount;

    private Integer selectedAudienceCount;

    private String idempotencyToken;

    private String orderRequestId;

    private Long orderId;

    private LocalDateTime expireTime;

    private LocalDateTime confirmedAt;

    private LocalDateTime completedAt;

    private LocalDateTime submittedAt;

    private String failReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

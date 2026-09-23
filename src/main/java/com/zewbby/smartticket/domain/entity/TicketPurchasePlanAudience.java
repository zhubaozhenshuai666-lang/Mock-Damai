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
@TableName("ticket_purchase_plan_audience")
public class TicketPurchasePlanAudience {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private Long audienceId;

    private String selectionType;

    private Integer lineNo;

    private String nameSnapshot;

    private String idNoHashSnapshot;

    private LocalDateTime createdAt;
}

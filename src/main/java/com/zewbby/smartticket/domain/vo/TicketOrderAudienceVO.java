package com.zewbby.smartticket.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单中的观演人快照。
 *
 * <p>订单读取只返回下单时固化的字段，不重新读取当前实名观演人资料，避免历史订单展示被资料修改影响。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketOrderAudienceVO {

    private Integer lineNo;

    private Long audienceId;

    private String nameSnapshot;

    private String idNoHashSnapshot;
}

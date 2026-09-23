package com.zewbby.smartticket.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    /**
     * @deprecated 用户身份必须以后端 JWT 解析出的 UserContext 为准，本字段仅为兼容旧 HTTP 示例保留。
     */
    @Deprecated
    private Long userId;

    @NotNull(message = "演出ID不能为空")
    private Long showId;

    @NotNull(message = "场次ID不能为空")
    private Long sessionId;

    @NotNull(message = "票档ID不能为空")
    private Long ticketCategoryId;

    @NotNull(message = "购票数量不能为空")
    @Positive(message = "购票数量必须大于0")
    private Integer quantity;

    @NotBlank(message = "幂等token不能为空")
    private String idempotencyToken;

    private String admissionToken;

    /** 预约抢票链路预先生成的请求 ID；普通下单由 OrderService 自行生成。 */
    @JsonIgnore
    private String requestId;

    /** 预约购买计划内部字段，仅由服务端填充，客户端不得直接改变。 */
    @JsonIgnore
    private Long purchasePlanId;

    /** 预约计划已校验的观演人，仅用于异步创单链路传递计划关联。 */
    @JsonIgnore
    private List<Long> selectedAudienceIds;

    public CreateOrderRequest(Long userId,
                              Long showId,
                              Long sessionId,
                              Long ticketCategoryId,
                              Integer quantity,
                              String idempotencyToken) {
        this.userId = userId;
        this.showId = showId;
        this.sessionId = sessionId;
        this.ticketCategoryId = ticketCategoryId;
        this.quantity = quantity;
        this.idempotencyToken = idempotencyToken;
    }
}

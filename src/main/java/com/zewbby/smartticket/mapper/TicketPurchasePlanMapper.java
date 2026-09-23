package com.zewbby.smartticket.mapper;

import com.zewbby.smartticket.domain.entity.TicketPurchasePlan;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TicketPurchasePlanMapper {

    int insert(TicketPurchasePlan plan);

    TicketPurchasePlan selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    List<TicketPurchasePlan> selectByUserId(@Param("userId") Long userId,
                                            @Param("status") String status,
                                            @Param("limit") Integer limit);

    int updateSpec(@Param("id") Long id,
                   @Param("userId") Long userId,
                   @Param("sessionId") Long sessionId,
                   @Param("ticketCategoryId") Long ticketCategoryId,
                   @Param("quantity") Integer quantity,
                   @Param("activePlanKey") String activePlanKey,
                   @Param("version") Integer version,
                   @Param("updatedAt") LocalDateTime updatedAt,
                   @Param("now") LocalDateTime now);

    int updateAudiences(@Param("id") Long id,
                        @Param("userId") Long userId,
                        @Param("version") Integer version,
                        @Param("selectedAudienceCount") Integer selectedAudienceCount,
                        @Param("updatedAt") LocalDateTime updatedAt,
                        @Param("now") LocalDateTime now);

    int complete(@Param("id") Long id,
                 @Param("userId") Long userId,
                 @Param("version") Integer version,
                 @Param("completedVersion") Integer completedVersion,
                 @Param("completedAt") LocalDateTime completedAt,
                 @Param("now") LocalDateTime now);

    int beginSubmitting(@Param("id") Long id,
                        @Param("userId") Long userId,
                        @Param("version") Integer version,
                        @Param("status") String status,
                        @Param("idempotencyToken") String idempotencyToken,
                        @Param("orderRequestId") String orderRequestId,
                        @Param("submittedAt") LocalDateTime submittedAt,
                        @Param("updatedAt") LocalDateTime updatedAt,
                        @Param("now") LocalDateTime now);

    int markOrderCreated(@Param("id") Long id,
                         @Param("orderRequestId") String orderRequestId,
                         @Param("orderId") Long orderId,
                         @Param("status") String status,
                         @Param("updatedAt") LocalDateTime updatedAt);

    int markFailed(@Param("id") Long id,
                   @Param("orderRequestId") String orderRequestId,
                   @Param("status") String status,
                   @Param("failReason") String failReason,
                   @Param("updatedAt") LocalDateTime updatedAt);

    int markReconciliationRequired(@Param("id") Long id,
                                   @Param("orderRequestId") String orderRequestId,
                                   @Param("status") String status,
                                   @Param("failReason") String failReason,
                                   @Param("updatedAt") LocalDateTime updatedAt);

    int cancelEditable(@Param("id") Long id,
                       @Param("userId") Long userId,
                       @Param("version") Integer version,
                       @Param("status") String status,
                       @Param("updatedAt") LocalDateTime updatedAt);

    int expireEditable(@Param("now") LocalDateTime now,
                       @Param("expiredStatus") String expiredStatus,
                       @Param("reason") String reason,
                       @Param("limit") Integer limit,
                       @Param("updatedAt") LocalDateTime updatedAt);

    int expireReadyAfterSale(@Param("now") LocalDateTime now,
                             @Param("expiredStatus") String expiredStatus,
                             @Param("reason") String reason,
                             @Param("limit") Integer limit,
                             @Param("updatedAt") LocalDateTime updatedAt);

    List<TicketPurchasePlan> selectStaleForReconciliation(@Param("before") LocalDateTime before,
                                                          @Param("limit") Integer limit);
}

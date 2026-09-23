package com.zewbby.smartticket.mapper;

import com.zewbby.smartticket.domain.entity.TicketPurchasePlanAudience;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TicketPurchasePlanAudienceMapper {

    int insertBatch(@Param("audiences") List<TicketPurchasePlanAudience> audiences);

    int deleteByPlanIdAndSelectionType(@Param("planId") Long planId,
                                        @Param("selectionType") String selectionType);

    List<TicketPurchasePlanAudience> selectByPlanIdAndSelectionType(@Param("planId") Long planId,
                                                                     @Param("selectionType") String selectionType);
}

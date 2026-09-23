package com.zewbby.smartticket.mapper;

import com.zewbby.smartticket.domain.entity.TicketOrderAudience;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TicketOrderAudienceMapper {

    int insertBatch(@Param("audiences") List<TicketOrderAudience> audiences);

    List<TicketOrderAudience> selectByOrderId(@Param("orderId") Long orderId);
}

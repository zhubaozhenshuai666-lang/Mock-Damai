package com.zewbby.smartticket.mapper;

import com.zewbby.smartticket.domain.entity.AudiencePerson;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AudiencePersonMapper {

    int insert(AudiencePerson audiencePerson);

    AudiencePerson selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    List<AudiencePerson> selectByIdsAndUserId(@Param("ids") List<Long> ids, @Param("userId") Long userId);

    List<AudiencePerson> selectByUserId(@Param("userId") Long userId);

    int updateByIdAndUserId(AudiencePerson audiencePerson);

    int disableByIdAndUserId(@Param("id") Long id,
                             @Param("userId") Long userId,
                             @Param("status") String status,
                             @Param("updatedAt") java.time.LocalDateTime updatedAt);
}

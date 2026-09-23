package com.zewbby.smartticket.service;

import com.zewbby.smartticket.domain.dto.CreateAudienceRequest;
import com.zewbby.smartticket.domain.dto.UpdateAudienceRequest;
import com.zewbby.smartticket.domain.vo.AudiencePersonVO;

import java.util.List;

public interface AudiencePersonService {

    AudiencePersonVO create(CreateAudienceRequest request);

    AudiencePersonVO update(Long id, UpdateAudienceRequest request);

    AudiencePersonVO get(Long id);

    List<AudiencePersonVO> list();

    void delete(Long id);
}

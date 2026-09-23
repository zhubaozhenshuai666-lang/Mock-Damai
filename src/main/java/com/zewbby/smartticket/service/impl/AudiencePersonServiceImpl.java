package com.zewbby.smartticket.service.impl;

import com.zewbby.smartticket.auth.UserContext;
import com.zewbby.smartticket.common.BusinessException;
import com.zewbby.smartticket.domain.dto.CreateAudienceRequest;
import com.zewbby.smartticket.domain.dto.UpdateAudienceRequest;
import com.zewbby.smartticket.domain.entity.AudiencePerson;
import com.zewbby.smartticket.domain.vo.AudiencePersonVO;
import com.zewbby.smartticket.enums.AudienceStatusEnum;
import com.zewbby.smartticket.mapper.AudiencePersonMapper;
import com.zewbby.smartticket.service.AudienceIdentityService;
import com.zewbby.smartticket.service.AudiencePersonService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AudiencePersonServiceImpl implements AudiencePersonService {

    private static final String DEFAULT_ID_TYPE = "ID_CARD";

    private final AudiencePersonMapper audiencePersonMapper;

    private final AudienceIdentityService audienceIdentityService;

    public AudiencePersonServiceImpl(AudiencePersonMapper audiencePersonMapper,
                                     AudienceIdentityService audienceIdentityService) {
        this.audiencePersonMapper = audiencePersonMapper;
        this.audienceIdentityService = audienceIdentityService;
    }

    @Override
    @Transactional
    public AudiencePersonVO create(CreateAudienceRequest request) {
        Long userId = UserContext.requireUserId();
        String idType = normalizeIdType(request.getIdType());
        String idNo = normalizeIdNo(request.getIdNo());
        LocalDateTime now = LocalDateTime.now();
        AudiencePerson audiencePerson = new AudiencePerson();
        audiencePerson.setUserId(userId);
        audiencePerson.setName(request.getName().trim());
        audiencePerson.setIdType(idType);
        audiencePerson.setIdNoCiphertext(audienceIdentityService.encrypt(idNo));
        audiencePerson.setIdNoHash(audienceIdentityService.hash(idNo));
        audiencePerson.setStatus(AudienceStatusEnum.ACTIVE.getCode());
        audiencePerson.setVersion(0);
        audiencePerson.setCreatedAt(now);
        audiencePerson.setUpdatedAt(now);
        try {
            audiencePersonMapper.insert(audiencePerson);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("该证件号已添加");
        }
        return toVO(audiencePerson);
    }

    @Override
    @Transactional
    public AudiencePersonVO update(Long id, UpdateAudienceRequest request) {
        Long userId = UserContext.requireUserId();
        AudiencePerson existing = getExisting(id, userId);
        String idType = normalizeIdType(request.getIdType());
        String idNo = normalizeIdNo(request.getIdNo());
        AudiencePerson update = new AudiencePerson();
        update.setId(id);
        update.setUserId(userId);
        update.setName(request.getName().trim());
        update.setIdType(idType);
        update.setIdNoCiphertext(audienceIdentityService.encrypt(idNo));
        update.setIdNoHash(audienceIdentityService.hash(idNo));
        update.setVersion(request.getVersion());
        update.setUpdatedAt(LocalDateTime.now());
        try {
            if (audiencePersonMapper.updateByIdAndUserId(update) != 1) {
                throw new BusinessException("观演人已被其他请求修改，请刷新后重试");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("该证件号已添加");
        }
        existing.setName(update.getName());
        existing.setIdType(update.getIdType());
        existing.setIdNoCiphertext(update.getIdNoCiphertext());
        existing.setIdNoHash(update.getIdNoHash());
        existing.setVersion(request.getVersion() + 1);
        existing.setUpdatedAt(update.getUpdatedAt());
        return toVO(existing);
    }

    @Override
    public AudiencePersonVO get(Long id) {
        return toVO(getExisting(id, UserContext.requireUserId()));
    }

    @Override
    public List<AudiencePersonVO> list() {
        return audiencePersonMapper.selectByUserId(UserContext.requireUserId())
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long userId = UserContext.requireUserId();
        if (audiencePersonMapper.disableByIdAndUserId(
                id,
                userId,
                AudienceStatusEnum.DISABLED.getCode(),
                LocalDateTime.now()) != 1) {
            throw new BusinessException("观演人不存在或已删除");
        }
    }

    private AudiencePerson getExisting(Long id, Long userId) {
        AudiencePerson audiencePerson = audiencePersonMapper.selectByIdAndUserId(id, userId);
        if (audiencePerson == null || !AudienceStatusEnum.ACTIVE.getCode().equals(audiencePerson.getStatus())) {
            throw new BusinessException("观演人不存在");
        }
        return audiencePerson;
    }

    private AudiencePersonVO toVO(AudiencePerson audiencePerson) {
        String idNo = audienceIdentityService.decrypt(audiencePerson.getIdNoCiphertext());
        AudiencePersonVO vo = new AudiencePersonVO();
        vo.setId(audiencePerson.getId());
        vo.setUserId(audiencePerson.getUserId());
        vo.setName(audiencePerson.getName());
        vo.setIdType(audiencePerson.getIdType());
        vo.setMaskedIdNo(audienceIdentityService.mask(idNo));
        vo.setStatus(audiencePerson.getStatus());
        vo.setVersion(audiencePerson.getVersion());
        vo.setCreatedAt(audiencePerson.getCreatedAt());
        vo.setUpdatedAt(audiencePerson.getUpdatedAt());
        return vo;
    }

    private String normalizeIdType(String idType) {
        return idType == null || idType.isBlank() ? DEFAULT_ID_TYPE : idType.trim();
    }

    private String normalizeIdNo(String idNo) {
        return idNo.trim().toUpperCase();
    }
}

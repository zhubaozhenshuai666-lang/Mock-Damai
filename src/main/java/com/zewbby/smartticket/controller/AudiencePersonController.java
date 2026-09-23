package com.zewbby.smartticket.controller;

import com.zewbby.smartticket.common.ApiResponse;
import com.zewbby.smartticket.domain.dto.CreateAudienceRequest;
import com.zewbby.smartticket.domain.dto.UpdateAudienceRequest;
import com.zewbby.smartticket.domain.vo.AudiencePersonVO;
import com.zewbby.smartticket.service.AudiencePersonService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audiences")
public class AudiencePersonController {

    private final AudiencePersonService audiencePersonService;

    public AudiencePersonController(AudiencePersonService audiencePersonService) {
        this.audiencePersonService = audiencePersonService;
    }

    @PostMapping
    public ApiResponse<AudiencePersonVO> create(@Valid @RequestBody CreateAudienceRequest request) {
        return ApiResponse.success(audiencePersonService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AudiencePersonVO> update(@PathVariable Long id,
                                                @Valid @RequestBody UpdateAudienceRequest request) {
        return ApiResponse.success(audiencePersonService.update(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<AudiencePersonVO> get(@PathVariable Long id) {
        return ApiResponse.success(audiencePersonService.get(id));
    }

    @GetMapping
    public ApiResponse<List<AudiencePersonVO>> list() {
        return ApiResponse.success(audiencePersonService.list());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        audiencePersonService.delete(id);
        return ApiResponse.success(null);
    }
}

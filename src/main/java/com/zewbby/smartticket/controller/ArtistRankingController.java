package com.zewbby.smartticket.controller;

import com.zewbby.smartticket.common.ApiResponse;
import com.zewbby.smartticket.domain.vo.ArtistRankingEntryVO;
import com.zewbby.smartticket.enums.ArtistRankingPeriodEnum;
import com.zewbby.smartticket.service.ArtistRankingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rankings")
public class ArtistRankingController {

    private final ArtistRankingService artistRankingService;

    public ArtistRankingController(ArtistRankingService artistRankingService) {
        this.artistRankingService = artistRankingService;
    }

    @GetMapping("/artists")
    public ApiResponse<List<ArtistRankingEntryVO>> top(
            @RequestParam(required = false, defaultValue = "weekly") String period,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(artistRankingService.top(ArtistRankingPeriodEnum.from(period), limit));
    }
}

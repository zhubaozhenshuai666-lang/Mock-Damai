package com.zewbby.smartticket.controller;

import com.zewbby.smartticket.common.ApiResponse;
import com.zewbby.smartticket.domain.vo.ShowListVO;
import com.zewbby.smartticket.ratelimit.ClientIpResolver;
import com.zewbby.smartticket.service.ShowSearchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class ShowSearchController {

    private final ShowSearchService showSearchService;

    private final ClientIpResolver clientIpResolver;

    public ShowSearchController(ShowSearchService showSearchService,
                                ClientIpResolver clientIpResolver) {
        this.showSearchService = showSearchService;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/shows")
    public ApiResponse<List<ShowListVO>> searchShows(
            @RequestParam String keyword,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request) {
        return ApiResponse.success(showSearchService.search(
                keyword,
                limit,
                clientIpResolver.resolve(request)));
    }
}

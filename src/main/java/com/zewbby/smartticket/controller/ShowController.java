package com.zewbby.smartticket.controller;

import com.zewbby.smartticket.common.ApiResponse;
import com.zewbby.smartticket.domain.vo.SessionVO;
import com.zewbby.smartticket.domain.vo.ShowDetailVO;
import com.zewbby.smartticket.domain.vo.ShowListVO;
import com.zewbby.smartticket.domain.vo.TicketCategoryVO;
import com.zewbby.smartticket.ratelimit.ClientIpResolver;
import com.zewbby.smartticket.service.ArtistRankingService;
import com.zewbby.smartticket.service.ShowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ShowController {

    private final ShowService showService;

    private final ArtistRankingService artistRankingService;

    private final ClientIpResolver clientIpResolver;

    public ShowController(ShowService showService,
                          ArtistRankingService artistRankingService,
                          ClientIpResolver clientIpResolver) {
        this.showService = showService;
        this.artistRankingService = artistRankingService;
        this.clientIpResolver = clientIpResolver;
    }

    /**
     * 打印所有演出信息
     * @return
     */
    @GetMapping("/shows")
    public ApiResponse<List<ShowListVO>> listShows() {
        return ApiResponse.success(showService.listShows());
    }

    /**
     * 根据show.id来获取演出详情
     * @param id
     * @return
     */
    @GetMapping("/shows/{id}")
    public ApiResponse<ShowDetailVO> getShowDetail(@PathVariable Long id,
                                                   HttpServletRequest request) {
        ShowDetailVO detail = showService.getShowDetail(id);
        artistRankingService.recordDetailClick(detail.getArtist(), clientIpResolver.resolve(request));
        return ApiResponse.success(detail);
    }

    /**
     *根据show.id查询该演出所有场次
     * @param id
     * @return
     */
    @GetMapping("/shows/{id}/sessions")
    public ApiResponse<List<SessionVO>> listSessions(@PathVariable Long id) {
        return ApiResponse.success(showService.listSessions(id));
    }

    /**
     * 根据session.id来查询对应所有的票档！
     * @param sessionId
     * @return
     */
    @GetMapping("/sessions/{sessionId}/ticket-categories")
    public ApiResponse<List<TicketCategoryVO>> listTicketCategories(@PathVariable Long sessionId) {
        return ApiResponse.success(showService.listTicketCategories(sessionId));
    }
}

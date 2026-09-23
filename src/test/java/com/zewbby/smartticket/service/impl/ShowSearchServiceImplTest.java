package com.zewbby.smartticket.service.impl;

import com.zewbby.smartticket.config.ArtistRankingProperties;
import com.zewbby.smartticket.domain.vo.ShowListVO;
import com.zewbby.smartticket.mapper.ShowMapper;
import com.zewbby.smartticket.service.ArtistRankingService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class ShowSearchServiceImplTest {

    @Test
    void onlyArtistMatchingKeywordContributesToRanking() {
        ShowMapper showMapper = mock(ShowMapper.class);
        ArtistRankingService rankingService = mock(ArtistRankingService.class);
        when(showMapper.selectShowListByKeyword(anyString(), anyInt())).thenReturn(List.of(
                new ShowListVO(1L, "周杰伦演唱会", "周杰伦", "上海", "场馆", null),
                new ShowListVO(2L, "热门音乐节", "许嵩", "北京", "场馆", null)
        ));

        ShowSearchServiceImpl service = new ShowSearchServiceImpl(
                showMapper, rankingService, new ArtistRankingProperties());
        List<ShowListVO> result = service.search("周杰伦", 20, "127.0.0.1");

        assertThat(result).hasSize(2);
        verify(rankingService).recordSearch("周杰伦", "127.0.0.1");
        verify(rankingService, never()).recordSearch("许嵩", "127.0.0.1");
    }
}

package com.zewbby.smartticket.service.impl;

import com.zewbby.smartticket.common.BusinessException;
import com.zewbby.smartticket.config.ArtistRankingProperties;
import com.zewbby.smartticket.domain.vo.ShowListVO;
import com.zewbby.smartticket.mapper.ShowMapper;
import com.zewbby.smartticket.service.ArtistRankingService;
import com.zewbby.smartticket.service.ShowSearchService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class ShowSearchServiceImpl implements ShowSearchService {

    private static final int DEFAULT_LIMIT = 20;

    private static final int MAX_KEYWORD_LENGTH = 64;

    private final ShowMapper showMapper;

    private final ArtistRankingService artistRankingService;

    private final ArtistRankingProperties rankingProperties;

    public ShowSearchServiceImpl(ShowMapper showMapper,
                                 ArtistRankingService artistRankingService,
                                 ArtistRankingProperties rankingProperties) {
        this.showMapper = showMapper;
        this.artistRankingService = artistRankingService;
        this.rankingProperties = rankingProperties;
    }

    @Override
    public List<ShowListVO> search(String keyword, Integer limit, String clientIp) {
        String normalizedKeyword = normalizeKeyword(keyword);
        int safeLimit = normalizeLimit(limit);
        List<ShowListVO> results = showMapper.selectShowListByKeyword(normalizedKeyword, safeLimit);
        results.stream()
                .map(ShowListVO::getArtist)
                .filter(artist -> artist != null && !artist.isBlank())
                .map(String::trim)
                .filter(artist -> matchesArtistKeyword(artist, normalizedKeyword))
                .distinct()
                .forEach(artist -> artistRankingService.recordSearch(artist, clientIp));
        return results;
    }

    private boolean matchesArtistKeyword(String artist, String keyword) {
        String normalizedArtist = artist.toLowerCase(Locale.ROOT);
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        return normalizedArtist.contains(normalizedKeyword) || normalizedKeyword.contains(normalizedArtist);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException("搜索关键词不能为空");
        }
        String normalized = keyword.trim().replaceAll("\\s+", " ");
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException("搜索关键词过长");
        }
        return normalized;
    }

    private int normalizeLimit(Integer limit) {
        int value = limit == null ? DEFAULT_LIMIT : limit;
        if (value <= 0) {
            throw new BusinessException("搜索条数必须大于0");
        }
        return Math.min(value, Math.max(1, rankingProperties.getMaxLimit()));
    }
}

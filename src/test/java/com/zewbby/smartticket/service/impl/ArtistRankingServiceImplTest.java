package com.zewbby.smartticket.service.impl;

import com.zewbby.smartticket.config.ArtistRankingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArtistRankingServiceImplTest {

    @Test
    void detailClickIsDeduplicatedAndWritesAllTimeDailyAndWeeklyZsets() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(DefaultRedisScript.class), any(List.class), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(1L);

        ArtistRankingProperties properties = new ArtistRankingProperties();
        ArtistRankingServiceImpl service = new ArtistRankingServiceImpl(redis, properties);
        service.recordDetailClick(" 周杰伦 ", "127.0.0.1");

        verify(redis).execute(any(DefaultRedisScript.class), any(List.class), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void duplicateIdentityDoesNotIncreaseScoreAgain() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(DefaultRedisScript.class), any(List.class), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(0L);

        ArtistRankingServiceImpl service = new ArtistRankingServiceImpl(redis, new ArtistRankingProperties());
        service.recordSearch("许嵩", "127.0.0.1");

        verify(redis).execute(any(DefaultRedisScript.class), any(List.class), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }
}

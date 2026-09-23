package com.zewbby.smartticket.service.impl;

import com.zewbby.smartticket.common.BusinessException;
import com.zewbby.smartticket.auth.UserContext;
import com.zewbby.smartticket.config.ArtistRankingProperties;
import com.zewbby.smartticket.constant.RedisKeyConstant;
import com.zewbby.smartticket.domain.vo.ArtistRankingEntryVO;
import com.zewbby.smartticket.enums.ArtistRankingComponentEnum;
import com.zewbby.smartticket.enums.ArtistRankingPeriodEnum;
import com.zewbby.smartticket.service.ArtistRankingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ArtistRankingServiceImpl implements ArtistRankingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtistRankingServiceImpl.class);

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final StringRedisTemplate stringRedisTemplate;

    private final ArtistRankingProperties properties;

    private final DefaultRedisScript<Long> recordScript;

    public ArtistRankingServiceImpl(StringRedisTemplate stringRedisTemplate,
                                    ArtistRankingProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.recordScript = buildScript("lua/artist_ranking_record.lua");
    }

    @Override
    public void recordDetailClick(String artist, String clientIp) {
        record(ArtistRankingComponentEnum.INTEREST, "detail", artist, clientIp,
                properties.getDetailClickWeight());
    }

    @Override
    public void recordSearch(String artist, String clientIp) {
        record(ArtistRankingComponentEnum.INTEREST, "search", artist, clientIp,
                properties.getSearchWeight());
    }

    @Override
    public void recordPurchaseIntent(String artist, String clientIp) {
        record(ArtistRankingComponentEnum.INTENT, "purchase", artist, clientIp,
                properties.getPurchaseIntentWeight());
    }

    @Override
    public void recordPaidOrder(String artist, String clientIp) {
        record(ArtistRankingComponentEnum.PAID, "paid", artist, clientIp,
                properties.getPaidOrderWeight());
    }

    @Override
    public void recordContentInteraction(String artist, String action, String clientIp) {
        String normalizedAction = action == null || action.isBlank() ? "content" : action.trim().toLowerCase(Locale.ROOT);
        record(ArtistRankingComponentEnum.CONTENT,
                normalizedAction,
                artist,
                clientIp,
                contentWeight(normalizedAction));
    }

    @Override
    public void recordTaskContribution(String artist, String taskId, String clientIp) {
        record(ArtistRankingComponentEnum.TASK,
                "task:" + (taskId == null || taskId.isBlank() ? "unknown" : taskId.trim()),
                artist,
                clientIp,
                15.0D);
    }

    @Override
    public List<ArtistRankingEntryVO> top(ArtistRankingPeriodEnum period, Integer limit) {
        int safeLimit = normalizeLimit(limit);
        if (!properties.isEnabled()) {
            return List.of();
        }
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples = readWeighted(period, safeLimit);
            if (tuples == null || tuples.isEmpty()) {
                return List.of();
            }
            List<ArtistRankingEntryVO> result = new ArrayList<>(tuples.size());
            int rank = 1;
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                if (tuple.getValue() == null) {
                    continue;
                }
                Object artistValue = stringRedisTemplate.opsForHash()
                        .get(RedisKeyConstant.artistRankingNamesKey(), tuple.getValue());
                String artistName = artistValue == null ? null : artistValue.toString();
                result.add(new ArtistRankingEntryVO(rank++,
                        artistName == null ? tuple.getValue() : artistName,
                        tuple.getScore()));
            }
            return result;
        } catch (RuntimeException exception) {
            LOGGER.warn("Artist ranking read degraded because Redis is unavailable, period={}", period, exception);
            return List.of();
        }
    }

    private void record(ArtistRankingComponentEnum component,
                        String action,
                        String artist,
                        String clientIp,
                        double weight) {
        if (!properties.isEnabled() || weight <= 0D) {
            return;
        }
        String normalizedArtist = normalizeArtist(artist);
        if (normalizedArtist.isBlank()) {
            return;
        }
        LocalDate today = LocalDateTime.now(BUSINESS_ZONE).toLocalDate();
        String day = today.format(DAY_FORMATTER);
        Long userId = UserContext.getUserId();
        String identity = userId == null
                ? "ip:" + sha256(clientIp == null ? "unknown" : clientIp.trim())
                : "user:" + userId;
        double qualityFactor = userId == null
                ? clamp(properties.getAnonymousQualityFactor(), 0.1D, 1.0D)
                : clamp(properties.getAuthenticatedQualityFactor(), 0.1D, 1.0D);
        String dedupKey = RedisKeyConstant.artistRankingDedupKey(
                "day:" + day,
                action,
                identity,
                sha256(normalizedArtist));
        try {
            String artistMember = "artist:" + sha256(normalizedArtist).substring(0, 32);
            String hour = LocalDateTime.now(BUSINESS_ZONE).truncatedTo(ChronoUnit.HOURS).format(HOUR_FORMATTER);
            Long result = stringRedisTemplate.execute(
                    recordScript,
                    Arrays.asList(
                            dedupKey,
                            RedisKeyConstant.artistRankingComponentKey(component.getCode(), "all"),
                            RedisKeyConstant.artistRankingComponentKey(component.getCode(), "daily:" + day),
                            RedisKeyConstant.artistRankingComponentKey(component.getCode(), "weekly:" + weekKey(today)),
                            RedisKeyConstant.artistRankingComponentKey(component.getCode(), "monthly:" + today.getYear() + "-" + today.getMonthValue()),
                            RedisKeyConstant.artistRankingComponentHourlyKey(component.getCode(), hour),
                            RedisKeyConstant.artistRankingNamesKey()
                    ),
                    artistMember,
                    String.valueOf(weight * qualityFactor),
                    String.valueOf(Math.max(1, properties.getDedupTtlSeconds())),
                    String.valueOf(Math.max(1, properties.getDailyKeyTtlSeconds())),
                    String.valueOf(Math.max(1, properties.getWeeklyKeyTtlSeconds())),
                    String.valueOf(Math.max(1, properties.getMonthlyKeyTtlSeconds())),
                    String.valueOf(Math.max(1, properties.getHourlyKeyTtlSeconds())),
                    String.valueOf(properties.getSaturationScale() > 0D
                            ? properties.getSaturationScale() : 1D),
                    normalizedArtist
            );
            if (Long.valueOf(0L).equals(result)) {
                return;
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Artist ranking write degraded because Redis is unavailable, action={}, artist={}",
                    action, normalizedArtist, exception);
        }
    }

    private Set<ZSetOperations.TypedTuple<String>> readWeighted(ArtistRankingPeriodEnum period, int limit) {
        if (period == ArtistRankingPeriodEnum.HOT) {
            return readHot(limit);
        }
        List<ArtistRankingComponentEnum> components = List.of(ArtistRankingComponentEnum.values());
        List<String> keys = components.stream()
                .map(component -> componentPeriodKey(component, period))
                .toList();
        return readMerged(keys, componentWeights(), limit, "period:" + period.getCode());
    }

    private String componentPeriodKey(ArtistRankingComponentEnum component, ArtistRankingPeriodEnum period) {
        LocalDate today = LocalDateTime.now(BUSINESS_ZONE).toLocalDate();
        return switch (period) {
            case ALL -> RedisKeyConstant.artistRankingComponentKey(component.getCode(), "all");
            case DAILY -> RedisKeyConstant.artistRankingComponentKey(component.getCode(), "daily:" + today.format(DAY_FORMATTER));
            case WEEKLY -> RedisKeyConstant.artistRankingComponentKey(component.getCode(), "weekly:" + weekKey(today));
            case MONTHLY -> RedisKeyConstant.artistRankingComponentKey(component.getCode(), "monthly:" + today.getYear() + "-" + today.getMonthValue());
            case HOT -> throw new IllegalArgumentException("hot 榜使用小时桶聚合");
        };
    }

    private Set<ZSetOperations.TypedTuple<String>> readHot(int limit) {
        LocalDateTime currentHour = LocalDateTime.now(BUSINESS_ZONE).truncatedTo(ChronoUnit.HOURS);
        int windowHours = Math.max(1, properties.getHotWindowHours());
        List<String> componentTemporaryKeys = new ArrayList<>();
        String temporaryKey = RedisKeyConstant.artistRankingKey("hot:query:" + UUID.randomUUID());
        try {
            double halfLife = properties.getHotHalfLifeHours() > 0D
                    ? properties.getHotHalfLifeHours() : 1D;
            for (ArtistRankingComponentEnum component : ArtistRankingComponentEnum.values()) {
                List<String> bucketKeys = new ArrayList<>(windowHours);
                for (int offset = 0; offset < windowHours; offset++) {
                    bucketKeys.add(RedisKeyConstant.artistRankingComponentHourlyKey(
                            component.getCode(),
                            currentHour.minusHours(offset).format(HOUR_FORMATTER)));
                }
                double[] decayWeights = new double[bucketKeys.size()];
                for (int offset = 0; offset < decayWeights.length; offset++) {
                    decayWeights[offset] = Math.pow(0.5D, offset / halfLife);
                }
                String componentTemporaryKey = RedisKeyConstant.artistRankingKey(
                        "hot:component:" + component.getCode() + ":" + UUID.randomUUID());
                componentTemporaryKeys.add(componentTemporaryKey);
                stringRedisTemplate.opsForZSet().unionAndStore(
                        bucketKeys.get(0),
                        bucketKeys.subList(1, bucketKeys.size()),
                        componentTemporaryKey,
                        Aggregate.SUM,
                        Weights.of(decayWeights));
            }
            return readMerged(componentTemporaryKeys, componentWeights(), limit, temporaryKey);
        } finally {
            componentTemporaryKeys.forEach(stringRedisTemplate::delete);
            stringRedisTemplate.delete(temporaryKey);
        }
    }

    private Set<ZSetOperations.TypedTuple<String>> readMerged(List<String> keys,
                                                               double[] weights,
                                                               int limit,
                                                               String temporaryKeySuffix) {
        String temporaryKey = temporaryKeySuffix.startsWith("ranking:")
                ? temporaryKeySuffix
                : RedisKeyConstant.artistRankingKey("query:" + temporaryKeySuffix + ":" + UUID.randomUUID());
        try {
            Long merged = stringRedisTemplate.opsForZSet().unionAndStore(
                    keys.get(0),
                    keys.subList(1, keys.size()),
                    temporaryKey,
                    Aggregate.SUM,
                    Weights.of(weights));
            if (merged == null || merged == 0L) {
                return Set.of();
            }
            stringRedisTemplate.expire(temporaryKey, Duration.ofSeconds(10));
            return stringRedisTemplate.opsForZSet().reverseRangeWithScores(temporaryKey, 0, limit - 1L);
        } finally {
            stringRedisTemplate.delete(temporaryKey);
        }
    }

    private double[] componentWeights() {
        return new double[]{
                positiveWeight(properties.getContentComponentWeight()),
                positiveWeight(properties.getInterestComponentWeight()),
                positiveWeight(properties.getIntentComponentWeight()),
                positiveWeight(properties.getPaidComponentWeight()),
                positiveWeight(properties.getTaskComponentWeight())
        };
    }

    private double contentWeight(String action) {
        return switch (action) {
            case "like" -> 1.0D;
            case "favorite", "collect" -> 2.0D;
            case "comment" -> 10.0D;
            case "share" -> 12.0D;
            case "publish" -> 15.0D;
            default -> 1.0D;
        };
    }

    private double positiveWeight(double value) {
        return value > 0D ? value : 0.0D;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String weekKey(LocalDate date) {
        WeekFields weekFields = WeekFields.ISO;
        return date.get(weekFields.weekBasedYear()) + "-W"
                + String.format(Locale.ROOT, "%02d", date.get(weekFields.weekOfWeekBasedYear()));
    }

    private int normalizeLimit(Integer limit) {
        int value = limit == null ? properties.getDefaultLimit() : limit;
        if (value <= 0) {
            throw new BusinessException("榜单条数必须大于0");
        }
        return Math.min(value, Math.max(1, properties.getMaxLimit()));
    }

    private String normalizeArtist(String artist) {
        if (artist == null) {
            return "";
        }
        String normalized = artist.trim().replaceAll("\\s+", " ");
        if (normalized.length() > properties.getArtistMaxLength()) {
            return normalized.substring(0, properties.getArtistMaxLength());
        }
        return normalized;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("排行榜身份摘要失败", exception);
        }
    }

    private DefaultRedisScript<Long> buildScript(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        script.setResultType(Long.class);
        return script;
    }
}

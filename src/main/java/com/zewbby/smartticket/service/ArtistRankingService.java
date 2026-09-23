package com.zewbby.smartticket.service;

import com.zewbby.smartticket.domain.vo.ArtistRankingEntryVO;
import com.zewbby.smartticket.enums.ArtistRankingPeriodEnum;

import java.util.List;

public interface ArtistRankingService {

    void recordDetailClick(String artist, String clientIp);

    void recordSearch(String artist, String clientIp);

    void recordPurchaseIntent(String artist, String clientIp);

    void recordPaidOrder(String artist, String clientIp);

    void recordContentInteraction(String artist, String action, String clientIp);

    void recordTaskContribution(String artist, String taskId, String clientIp);

    List<ArtistRankingEntryVO> top(ArtistRankingPeriodEnum period, Integer limit);
}

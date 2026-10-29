package com.campustrain.growthservice.service;

import com.campustrain.growthservice.dto.LearningCompletedEvent;
import com.campustrain.growthservice.vo.RankView;
import java.util.List;

public interface GrowthService {
    String TOTAL_RANKING_KEY = "campustrain:growth:ranking:total";

    boolean award(LearningCompletedEvent event);
    List<RankView> top(String key, int limit);
    RankView mine(String key, long userId);
    void rebuild(String month);
    void settle(String requestedSeason);

    static String monthRankingKey(String month) {
        return "campustrain:growth:ranking:month:" + month;
    }
}

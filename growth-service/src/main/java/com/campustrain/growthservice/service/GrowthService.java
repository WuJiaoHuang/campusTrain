package com.campustrain.growthservice.service;

import com.campustrain.growthservice.dto.LearningCompletedEvent;
import com.campustrain.growthservice.dto.CommunityActionEvent;
import com.campustrain.growthservice.vo.RankView;
import java.util.List;

public interface GrowthService {
    String TOTAL_RANKING_KEY = "campustrain:growth:ranking:total";

    boolean awardLearningCompletedPoints(LearningCompletedEvent event);
    boolean awardCommunityActionPoints(CommunityActionEvent event);
    List<RankView> getTopRanking(String rankingKey, int limit);
    RankView getMyRanking(String rankingKey, long userId);
    void rebuildRankings(String month);
    void settleSeason(String requestedSeason);

    static String monthRankingKey(String month) {
        return "campustrain:growth:ranking:month:" + month;
    }
}

package com.campustrain.growthservice.controller;

import com.campustrain.growthservice.service.GrowthService;
import com.campustrain.growthservice.vo.RankView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/growth")
@RequiredArgsConstructor
public class GrowthController {

    private final GrowthService growthService;

    @GetMapping("/get/total-ranking")
    public List<RankView> getTotalRanking(@RequestParam(name = "limit", defaultValue = "10") int limit) {
        return growthService.getTopRanking(GrowthService.TOTAL_RANKING_KEY, validateRankingLimit(limit));
    }

    @GetMapping("/get/monthly-ranking")
    public List<RankView> getMonthlyRanking(@RequestParam(name = "month") String month,
                                            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return growthService.getTopRanking(GrowthService.monthRankingKey(month), validateRankingLimit(limit));
    }

    @GetMapping("/get/my-ranking")
    public RankView getMyRanking(@RequestHeader(name = "X-User-Id") long userId) {
        return growthService.getMyRanking(GrowthService.TOTAL_RANKING_KEY, userId);
    }

    @GetMapping("/get/my-monthly-ranking")
    public RankView getMyMonthlyRanking(@RequestParam(name = "month") String month,
                                        @RequestHeader(name = "X-User-Id") long userId) {
        return growthService.getMyRanking(GrowthService.monthRankingKey(month), userId);
    }

    private int validateRankingLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be in 1..100");
        }
        return limit;
    }
}

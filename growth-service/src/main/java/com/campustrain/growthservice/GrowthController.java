package com.campustrain.growthservice;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/growth/rankings")
class GrowthController {
    private final GrowthService growthService;
    GrowthController(GrowthService growthService) { this.growthService = growthService; }

    @GetMapping("/total")
    List<RankView> total(@RequestParam(name = "limit", defaultValue = "10") int limit) {
        return growthService.top(GrowthService.TOTAL_RANKING_KEY, validLimit(limit));
    }

    @GetMapping("/monthly")
    List<RankView> monthly(@RequestParam(name = "month") String month, @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return growthService.top(GrowthService.monthRankingKey(month), validLimit(limit));
    }

    @GetMapping("/me")
    RankView mine(@RequestHeader(name = "X-User-Id") long userId) {
        return growthService.mine(GrowthService.TOTAL_RANKING_KEY, userId);
    }

    @GetMapping("/me/monthly")
    RankView monthlyMine(@RequestParam(name = "month") String month, @RequestHeader(name = "X-User-Id") long userId) {
        return growthService.mine(GrowthService.monthRankingKey(month), userId);
    }

    private int validLimit(int limit) {
        if (limit < 1 || limit > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be in 1..100");
        return limit;
    }
}

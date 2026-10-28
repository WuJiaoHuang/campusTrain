package com.campustrain.growthservice;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GrowthJob {
    private final GrowthService growthService;
    GrowthJob(GrowthService growthService) { this.growthService = growthService; }

    @XxlJob("growthSeasonSettlementJob")
    public void settleSeason() {
        String season = XxlJobHelper.getJobParam();
        try {
            growthService.settle(season);
            log.info("XXL-JOB season settlement finished, season={}", season);
        } catch (RuntimeException exception) {
            log.error("XXL-JOB season settlement failed, season={}", season, exception);
            throw exception;
        }
    }

    @XxlJob("growthRankingRebuildJob")
    public void rebuildRanking() {
        String month = XxlJobHelper.getJobParam();
        try {
            growthService.rebuild(month);
            log.info("XXL-JOB ranking rebuild finished, month={}", month);
        } catch (RuntimeException exception) {
            log.error("XXL-JOB ranking rebuild failed, month={}", month, exception);
            throw exception;
        }
    }
}

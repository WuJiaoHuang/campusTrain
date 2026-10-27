package com.campustrain.learningservice.vo;

import java.time.Instant;

public record LearningStartVO(long lessonId, long trainingId, int videoDuration, String playUrl,
                              Instant playUrlExpireAt, long resumePositionSeconds, boolean completed) {
}

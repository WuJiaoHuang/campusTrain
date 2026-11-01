package com.campustrain.trainingservice.service;

import com.campustrain.trainingservice.vo.LessonInfoVO;
import com.campustrain.trainingservice.vo.VideoPlayVO;

public interface InternalLearningService {
    LessonInfoVO getLessonInfo(Long lessonId);
    VideoPlayVO getLessonPlayUrl(Long lessonId);
    Long getTrainingIdByChapter(Long chapterId);
}

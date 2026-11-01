package com.campustrain.learningservice.service;

import com.campustrain.learningservice.vo.LearningProgressVO;
import com.campustrain.learningservice.vo.LearningStartVO;
import com.campustrain.learningservice.vo.LearningSummaryVO;

public interface LearningService {
    LearningStartVO startLearning(Long lessonId);
    LearningProgressVO updateLearningProgress(Long lessonId, Integer progressSeconds);
    LearningProgressVO getLearningProgress(Long lessonId);
    void completeTextLesson(Long lessonId);
    LearningSummaryVO getTrainingProgress(Long trainingId);
    LearningSummaryVO getChapterProgress(Long chapterId);
}

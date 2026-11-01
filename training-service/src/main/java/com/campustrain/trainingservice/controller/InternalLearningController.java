package com.campustrain.trainingservice.controller;

import com.campustrain.trainingservice.common.Result;
import com.campustrain.trainingservice.service.InternalLearningService;
import com.campustrain.trainingservice.vo.LessonInfoVO;
import com.campustrain.trainingservice.vo.VideoPlayVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/learning")
public class InternalLearningController {
    private final InternalLearningService internalLearningService;

    @GetMapping("/get-lesson/{lessonId}")
    public Result<LessonInfoVO> getLessonInfo(@PathVariable("lessonId") Long lessonId) {
        return Result.success(internalLearningService.getLessonInfo(lessonId));
    }

    @GetMapping("/get-play-url/{lessonId}")
    public Result<VideoPlayVO> getLessonPlayUrl(@PathVariable("lessonId") Long lessonId) {
        return Result.success(internalLearningService.getLessonPlayUrl(lessonId));
    }

    @GetMapping("/get-training-by-chapter/{chapterId}")
    public Result<Long> getTrainingIdByChapter(@PathVariable("chapterId") Long chapterId) {
        return Result.success(internalLearningService.getTrainingIdByChapter(chapterId));
    }
}

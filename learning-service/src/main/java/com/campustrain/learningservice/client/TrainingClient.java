package com.campustrain.learningservice.client;

import com.campustrain.learningservice.client.dto.Catalog;
import com.campustrain.learningservice.client.dto.Lesson;
import com.campustrain.learningservice.client.dto.PlayUrl;
import com.campustrain.learningservice.client.dto.RemoteResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "training-service", contextId = "learningTraining")
public interface TrainingClient {
    @GetMapping("/internal/learning/get-lesson/{lessonId}")
    RemoteResult<Lesson> getLessonById(@PathVariable("lessonId") Long lessonId);

    @GetMapping("/internal/learning/get-play-url/{lessonId}")
    RemoteResult<PlayUrl> getLessonPlayUrl(@PathVariable("lessonId") Long lessonId);

    @GetMapping("/training/get/catalog/{trainingId}")
    RemoteResult<Catalog> getTrainingCatalog(@PathVariable("trainingId") Long trainingId);

    @GetMapping("/internal/learning/get-training-by-chapter/{chapterId}")
    RemoteResult<Long> getTrainingIdByChapter(@PathVariable("chapterId") Long chapterId);
}

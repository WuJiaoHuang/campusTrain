package com.campustrain.communityservice.client;

import com.campustrain.communityservice.client.dto.LessonInfo;
import com.campustrain.communityservice.client.dto.Catalog;
import com.campustrain.communityservice.client.dto.RemoteResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "training-service", contextId = "communityTraining")
public interface TrainingClient {
    //根据id找training对象
    @GetMapping("/internal/training/get-enrollment-check/{trainingId}")
    RemoteResult<Object> getTrainingEnrollmentCheck(@PathVariable("trainingId") Long trainingId);
    @GetMapping("/internal/learning/get-lesson/{lessonId}")
    RemoteResult<LessonInfo> getLessonInfo(@PathVariable("lessonId") Long lessonId);
    @GetMapping("/training/get/catalog/{trainingId}")
    RemoteResult<Catalog> getTrainingCatalog(@PathVariable("trainingId") Long trainingId);
}

package com.campustrain.communityservice.client;

import com.campustrain.communityservice.client.dto.LessonInfo;
import com.campustrain.communityservice.client.dto.Catalog;
import com.campustrain.communityservice.client.dto.RemoteResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "training-service", contextId = "communityTraining")
public interface TrainingClient {
    @GetMapping("/internal/trainings/{id}/enrollment-check")
    RemoteResult<Object> training(@PathVariable("id") Long id);
    @GetMapping("/internal/learning/lessons/{id}")
    RemoteResult<LessonInfo> lesson(@PathVariable("id") Long id);
    @GetMapping("/trainings/{id}/catalog")
    RemoteResult<Catalog> catalog(@PathVariable("id") Long id);
}

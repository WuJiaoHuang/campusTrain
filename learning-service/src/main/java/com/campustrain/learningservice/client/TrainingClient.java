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
    @GetMapping("/internal/learning/lessons/{id}")
    RemoteResult<Lesson> lesson(@PathVariable("id") Long id);

    @GetMapping("/internal/learning/lessons/{id}/play-url")
    RemoteResult<PlayUrl> playUrl(@PathVariable("id") Long id);

    @GetMapping("/trainings/{id}/catalog")
    RemoteResult<Catalog> catalog(@PathVariable("id") Long id);

    @GetMapping("/internal/learning/chapters/{id}/training")
    RemoteResult<Long> chapterTraining(@PathVariable("id") Long id);
}

package com.campustrain.enrollmentservice.client;

import com.campustrain.enrollmentservice.remote.RemoteResult;
import com.campustrain.enrollmentservice.remote.vo.RemoteTrainingEnrollmentCheckVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "training-service", fallbackFactory = TrainingClientFallbackFactory.class)
public interface TrainingClient {

    @GetMapping("/internal/trainings/{id}/enrollment-check")
    RemoteResult<RemoteTrainingEnrollmentCheckVO> getTrainingById(@PathVariable("id") Long id);
}

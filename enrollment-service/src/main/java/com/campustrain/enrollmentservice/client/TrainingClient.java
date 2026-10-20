package com.campustrain.enrollmentservice.client;

import com.campustrain.enrollmentservice.remote.RemoteResult;
import com.campustrain.enrollmentservice.remote.vo.RemoteTrainingEnrollmentCheckVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "training-service", fallbackFactory = TrainingClientFallbackFactory.class)
public interface TrainingClient {

    @GetMapping("/internal/trainings/{id}/enrollment-check")
    RemoteResult<RemoteTrainingEnrollmentCheckVO> getTrainingById(@PathVariable("id") Long id);

    @PostMapping("/internal/trainings/{id}/enrollment-count/increment")
    RemoteResult<Void> increment(@PathVariable("id") Long id);

    @PostMapping("/internal/trainings/{id}/enrollment-count/decrement")
    RemoteResult<Void> decrement(@PathVariable("id") Long id);

    @PostMapping("/internal/trainings/{id}/enrollment-count/restore")
    RemoteResult<Void> restore(@PathVariable("id") Long id);
}

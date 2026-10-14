package com.campustrain.enrollmentservice.client;

import com.campustrain.enrollmentservice.remote.RemoteResult;
import com.campustrain.enrollmentservice.remote.vo.RemoteTrainingVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "training-service")
public interface TrainingClient {

    @GetMapping("/trainings/{id}")
    RemoteResult<RemoteTrainingVO> getTrainingById(@PathVariable("id") Long id);
}

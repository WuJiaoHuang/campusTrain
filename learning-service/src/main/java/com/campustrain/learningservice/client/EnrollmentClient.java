package com.campustrain.learningservice.client;

import com.campustrain.learningservice.client.dto.RemoteResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "enrollment-service", contextId = "learningEnrollment")
public interface EnrollmentClient {
    @GetMapping("/internal/learning/enrollments/{trainingId}/users/{userId}")
    RemoteResult<Boolean> active(@PathVariable("trainingId") Long trainingId,
                                 @PathVariable("userId") Long userId);
}

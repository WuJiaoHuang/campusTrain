package com.campustrain.enrollmentservice.client;

import com.campustrain.enrollmentservice.remote.RemoteResult;
import com.campustrain.enrollmentservice.remote.vo.RemoteUserVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", fallbackFactory = UserClientFallbackFactory.class)
public interface UserClient {

    @GetMapping("/users/{id}")
    RemoteResult<RemoteUserVO> getUserById(@PathVariable("id") Long id);
}

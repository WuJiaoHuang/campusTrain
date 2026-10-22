package com.campustrain.enrollmentservice.client;

import com.campustrain.enrollmentservice.remote.RemoteResult;
import com.campustrain.enrollmentservice.remote.vo.RemoteTrainingEnrollmentCheckVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "training-service", fallbackFactory = TrainingClientFallbackFactory.class)
public interface TrainingClient {

    //查询培训报名校验信息；返回 RemoteResult<RemoteTrainingEnrollmentCheckVO>，里面是真正的培训信息，比如 trainingId、teacherId、status、capacity、enrolledCount、enrollStartTime、enrollEndTime。
    @GetMapping("/internal/trainings/{id}/enrollment-check")
    RemoteResult<RemoteTrainingEnrollmentCheckVO> getTrainingById(@PathVariable("id") Long id);

    //尝试给指定id的training_cout加1,如果失败返回400状态码
    @PostMapping("/internal/trainings/{id}/enrollment-count/increment")
    RemoteResult<Void> increment(@PathVariable("id") Long id);

    //尝试给指定id的training_count减1，如果失败返回400状态码
    @PostMapping("/internal/trainings/{id}/enrollment-count/decrement")
    RemoteResult<Void> decrement(@PathVariable("id") Long id);
    //补偿恢复，当用户取消报名的时候补偿training减掉的名额
    @PostMapping("/internal/trainings/{id}/enrollment-count/restore")
    RemoteResult<Void> restore(@PathVariable("id") Long id);
}

package com.campustrain.enrollmentservice.client;

import com.campustrain.enrollmentservice.context.UserContext;
import com.campustrain.enrollmentservice.remote.RemoteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TrainingClientFallbackFactory implements FallbackFactory<TrainingClient> {
    @Override
    public TrainingClient create(Throwable cause) {
        return new TrainingClient() {
            private <T> RemoteResult<T> unavailable(Long trainingId) {
                log.error("training Feign fallback, traceId={}, userId={}, trainingId={}", UserContext.getTraceId(), UserContext.getUserId(), trainingId, cause);
                return RemoteResult.unavailable("培训服务暂时不可用");
            }
            @Override public RemoteResult<com.campustrain.enrollmentservice.remote.vo.RemoteTrainingEnrollmentCheckVO> getTrainingEnrollmentCheck(Long trainingId) { return unavailable(trainingId); }
            @Override public RemoteResult<Void> incrementEnrollmentCount(Long trainingId) { return unavailable(trainingId); }
            @Override public RemoteResult<Void> decrementEnrollmentCount(Long trainingId) { return unavailable(trainingId); }
            @Override public RemoteResult<Void> restoreEnrollmentCount(Long trainingId) { return unavailable(trainingId); }
        };
    }
}

package com.campustrain.enrollmentservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campustrain.enrollmentservice.entity.MqConsumeRecord;
import com.campustrain.enrollmentservice.entity.NotificationRecord;
import com.campustrain.enrollmentservice.mapper.MqConsumeRecordMapper;
import com.campustrain.enrollmentservice.mapper.NotificationRecordMapper;
import com.campustrain.enrollmentservice.mq.MqConstants;
import com.campustrain.enrollmentservice.mq.event.EnrollmentSuccessEvent;
import com.campustrain.enrollmentservice.service.NotificationService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    private static final int MAX_ATTEMPTS = 3;
    private final MqConsumeRecordMapper mqConsumeRecordMapper;
    private final NotificationRecordMapper notificationRecordMapper;
    private final PlatformTransactionManager platformTransactionManager;

    @Override
    public void processEnrollmentSuccess(EnrollmentSuccessEvent event) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                new TransactionTemplate(platformTransactionManager).executeWithoutResult(status -> processOnce(event));
                return;
            } catch (DuplicateKeyException ignored) {
                return;
            } catch (Exception exception) {
                last = exception;
                log.warn("MQ enrollment notification retry, eventId={}, attempt={}", event.getEventId(), attempt, exception);
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(100L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw interrupted;
                    }
                }
            }
        }
        throw last == null ? new IllegalStateException("consumer processing failed") : last;
    }

    private void processOnce(EnrollmentSuccessEvent event) {
        if (mqConsumeRecordMapper.selectCount(new LambdaQueryWrapper<MqConsumeRecord>()
                .eq(MqConsumeRecord::getEventId, event.getEventId())
                .eq(MqConsumeRecord::getConsumerName, MqConstants.NOTIFICATION_CONSUMER)) > 0) return;
        LocalDateTime now = LocalDateTime.now();
        MqConsumeRecord record = new MqConsumeRecord();
        record.setEventId(event.getEventId());
        record.setConsumerName(MqConstants.NOTIFICATION_CONSUMER);
        record.setEventType(event.getEventType());
        record.setStatus("DONE");
        record.setCreateTime(now);
        record.setUpdateTime(now);
        mqConsumeRecordMapper.insert(record);

        NotificationRecord notification = new NotificationRecord();
        notification.setEventId(event.getEventId());
        notification.setUserId(event.getUserId());
        notification.setTrainingId(event.getTrainingId());
        notification.setMessage("报名成功通知");
        notification.setCreateTime(now);
        notificationRecordMapper.insert(notification);
    }
}

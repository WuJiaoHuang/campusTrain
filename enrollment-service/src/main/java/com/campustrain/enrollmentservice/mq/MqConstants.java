package com.campustrain.enrollmentservice.mq;

public final class MqConstants {
    public static final String BUSINESS_EXCHANGE = "campustrain.business.exchange";
    public static final String DLX = "campustrain.dlx";
    public static final String ENROLLMENT_SUCCESS_ROUTING_KEY = "enrollment.success";
    public static final String LEARNING_COMPLETED_ROUTING_KEY = "learning.completed";
    public static final String ENROLLMENT_SUCCESS_QUEUE = "campustrain.enrollment.success.queue";
    public static final String ENROLLMENT_SUCCESS_DLQ = "campustrain.enrollment.success.dlq";
    public static final String NOTIFICATION_CONSUMER = "enrollment-success-notification";
    private MqConstants() { }
}

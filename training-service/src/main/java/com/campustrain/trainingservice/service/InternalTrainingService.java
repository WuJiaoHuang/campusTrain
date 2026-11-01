package com.campustrain.trainingservice.service;

public interface InternalTrainingService {
    boolean incrementEnrollmentCount(Long trainingId);
    boolean decrementEnrollmentCount(Long trainingId);
    boolean restoreEnrollmentCount(Long trainingId);
}

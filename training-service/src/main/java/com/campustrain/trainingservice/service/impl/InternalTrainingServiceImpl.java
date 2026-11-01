package com.campustrain.trainingservice.service.impl;

import com.campustrain.trainingservice.cache.TrainingCacheService;
import com.campustrain.trainingservice.mapper.TrainingMapper;
import com.campustrain.trainingservice.service.InternalTrainingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InternalTrainingServiceImpl implements InternalTrainingService {
    private final TrainingMapper trainingMapper;
    private final TrainingCacheService trainingCacheService;

    @Override
    public boolean incrementEnrollmentCount(Long trainingId) {
        if (trainingMapper.reserveEnrollment(trainingId) != 1) return false;
        trainingCacheService.evictDetail(trainingId);
        return true;
    }

    @Override
    public boolean decrementEnrollmentCount(Long trainingId) {
        if (trainingMapper.releaseEnrollment(trainingId) != 1) return false;
        trainingCacheService.evictDetail(trainingId);
        return true;
    }

    @Override
    public boolean restoreEnrollmentCount(Long trainingId) {
        if (trainingMapper.restoreEnrollment(trainingId) != 1) return false;
        trainingCacheService.evictDetail(trainingId);
        return true;
    }
}

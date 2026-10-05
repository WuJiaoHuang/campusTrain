package com.campustrain.trainingservice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campustrain.trainingservice.dto.TrainingCreateDTO;
import com.campustrain.trainingservice.dto.TrainingPageQueryDTO;
import com.campustrain.trainingservice.dto.TrainingUpdateDTO;
import com.campustrain.trainingservice.entity.Training;
import com.campustrain.trainingservice.vo.TrainingCatalogVO;
import com.campustrain.trainingservice.vo.TrainingVO;

public interface TrainingService extends IService<Training> {

    Long createTraining(TrainingCreateDTO dto);

    TrainingVO getTrainingDetail(Long id);

    void updateTraining(TrainingUpdateDTO dto);

    Page<TrainingVO> pageTrainings(TrainingPageQueryDTO dto);

    void changeStatus(Long id, Integer targetStatus);

    TrainingCatalogVO getTrainingCatalog(Long trainingId);
}

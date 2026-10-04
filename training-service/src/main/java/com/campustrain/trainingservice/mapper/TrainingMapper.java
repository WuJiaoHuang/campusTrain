package com.campustrain.trainingservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campustrain.trainingservice.entity.Training;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TrainingMapper extends BaseMapper<Training> {
}

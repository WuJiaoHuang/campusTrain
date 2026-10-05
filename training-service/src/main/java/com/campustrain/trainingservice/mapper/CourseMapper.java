package com.campustrain.trainingservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campustrain.trainingservice.entity.Course;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {
}

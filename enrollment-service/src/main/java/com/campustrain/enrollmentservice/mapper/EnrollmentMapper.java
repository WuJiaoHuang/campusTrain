package com.campustrain.enrollmentservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campustrain.enrollmentservice.entity.Enrollment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EnrollmentMapper extends BaseMapper<Enrollment> {
}

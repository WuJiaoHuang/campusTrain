package com.campustrain.trainingservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campustrain.trainingservice.entity.Training;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TrainingMapper extends BaseMapper<Training> {
    @Update("UPDATE training SET enrolled_count = enrolled_count + 1 WHERE id = #{id} AND status = 1 "
            + "AND enrolled_count < capacity AND (enroll_start_time IS NULL OR enroll_start_time <= NOW()) "
            + "AND (enroll_end_time IS NULL OR enroll_end_time >= NOW())")
    int reserveEnrollment(@Param("id") Long id);

    @Update("UPDATE training SET enrolled_count = enrolled_count - 1 WHERE id = #{id} AND enrolled_count > 0")
    int releaseEnrollment(@Param("id") Long id);

    @Update("UPDATE training SET enrolled_count = enrolled_count + 1 WHERE id = #{id}")
    int restoreEnrollment(@Param("id") Long id);
}

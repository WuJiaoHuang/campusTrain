package com.campustrain.trainingservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campustrain.trainingservice.dto.CourseCreateDTO;
import com.campustrain.trainingservice.dto.CourseUpdateDTO;
import com.campustrain.trainingservice.entity.Course;
import com.campustrain.trainingservice.vo.CourseVO;
import java.util.List;

public interface CourseService extends IService<Course> {

    Long createCourse(CourseCreateDTO dto);

    void updateCourse(CourseUpdateDTO dto);

    CourseVO getCourseDetail(Long id);

    List<CourseVO> listByTrainingId(Long trainingId);

    void deleteCourse(Long id);
}

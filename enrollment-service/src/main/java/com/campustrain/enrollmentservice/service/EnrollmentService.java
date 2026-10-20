package com.campustrain.enrollmentservice.service;

import com.campustrain.enrollmentservice.dto.EnrollmentCreateDTO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campustrain.enrollmentservice.vo.EnrollmentVO;

public interface EnrollmentService {

    Long enroll(EnrollmentCreateDTO dto);

    void cancel(Long enrollmentId);

    Page<EnrollmentVO> myEnrollments(long page, long size, Integer status);

    Page<EnrollmentVO> trainingEnrollments(Long trainingId, long page, long size, Integer status);
}

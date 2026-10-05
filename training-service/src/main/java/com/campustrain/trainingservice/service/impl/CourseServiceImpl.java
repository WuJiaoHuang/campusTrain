package com.campustrain.trainingservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campustrain.trainingservice.dto.CourseCreateDTO;
import com.campustrain.trainingservice.dto.CourseUpdateDTO;
import com.campustrain.trainingservice.entity.Chapter;
import com.campustrain.trainingservice.entity.Course;
import com.campustrain.trainingservice.entity.Training;
import com.campustrain.trainingservice.exception.BusinessException;
import com.campustrain.trainingservice.mapper.ChapterMapper;
import com.campustrain.trainingservice.mapper.CourseMapper;
import com.campustrain.trainingservice.mapper.TrainingMapper;
import com.campustrain.trainingservice.service.CourseService;
import com.campustrain.trainingservice.vo.CourseVO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {

    private final TrainingMapper trainingMapper;
    private final ChapterMapper chapterMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCourse(CourseCreateDTO dto) {
        ensureTrainingExists(dto.getTrainingId());

        LocalDateTime now = LocalDateTime.now();
        Course course = new Course();
        course.setTrainingId(dto.getTrainingId());
        course.setTitle(dto.getTitle());
        course.setDescription(dto.getDescription());
        course.setSort(dto.getSort());
        course.setCreateTime(now);
        course.setUpdateTime(now);

        save(course);
        log.info("course created, courseId={}, trainingId={}, title={}", course.getId(), course.getTrainingId(), course.getTitle());
        return course.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCourse(CourseUpdateDTO dto) {
        Course course = getExistingCourse(dto.getId());
        course.setTitle(dto.getTitle());
        course.setDescription(dto.getDescription());
        course.setSort(dto.getSort());
        course.setUpdateTime(LocalDateTime.now());

        updateById(course);
        log.info("course updated, courseId={}", course.getId());
    }

    @Override
    public CourseVO getCourseDetail(Long id) {
        return convertToVO(getExistingCourse(id));
    }

    @Override
    public List<CourseVO> listByTrainingId(Long trainingId) {
        ensureTrainingExists(trainingId);
        return list(new LambdaQueryWrapper<Course>()
                .eq(Course::getTrainingId, trainingId)
                .orderByAsc(Course::getSort)
                .orderByAsc(Course::getId))
                .stream()
                .map(this::convertToVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCourse(Long id) {
        Course course = getExistingCourse(id);
        Long chapterCount = chapterMapper.selectCount(new LambdaQueryWrapper<Chapter>().eq(Chapter::getCourseId, id));
        if (chapterCount > 0) {
            log.warn("course delete rejected, courseId={}, chapterCount={}", id, chapterCount);
            throw new BusinessException("课程下存在章节，无法删除");
        }

        removeById(id);
        log.info("course deleted, courseId={}", course.getId());
    }

    private void ensureTrainingExists(Long trainingId) {
        Training training = trainingMapper.selectById(trainingId);
        if (training == null) {
            throw new BusinessException("培训不存在");
        }
    }

    private Course getExistingCourse(Long id) {
        Course course = getById(id);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        return course;
    }

    private CourseVO convertToVO(Course course) {
        CourseVO vo = new CourseVO();
        vo.setId(course.getId());
        vo.setTrainingId(course.getTrainingId());
        vo.setTitle(course.getTitle());
        vo.setDescription(course.getDescription());
        vo.setSort(course.getSort());
        vo.setCreateTime(course.getCreateTime());
        vo.setUpdateTime(course.getUpdateTime());
        return vo;
    }
}

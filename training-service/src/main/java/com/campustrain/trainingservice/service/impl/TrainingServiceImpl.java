package com.campustrain.trainingservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campustrain.trainingservice.dto.TrainingCreateDTO;
import com.campustrain.trainingservice.dto.TrainingPageQueryDTO;
import com.campustrain.trainingservice.dto.TrainingUpdateDTO;
import com.campustrain.trainingservice.entity.Chapter;
import com.campustrain.trainingservice.entity.Course;
import com.campustrain.trainingservice.entity.Lesson;
import com.campustrain.trainingservice.entity.Training;
import com.campustrain.trainingservice.exception.BusinessException;
import com.campustrain.trainingservice.mapper.ChapterMapper;
import com.campustrain.trainingservice.mapper.CourseMapper;
import com.campustrain.trainingservice.mapper.LessonMapper;
import com.campustrain.trainingservice.mapper.TrainingMapper;
import com.campustrain.trainingservice.service.TrainingService;
import com.campustrain.trainingservice.vo.ChapterCatalogVO;
import com.campustrain.trainingservice.vo.CourseCatalogVO;
import com.campustrain.trainingservice.vo.LessonCatalogVO;
import com.campustrain.trainingservice.vo.TrainingCatalogVO;
import com.campustrain.trainingservice.vo.TrainingVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingServiceImpl extends ServiceImpl<TrainingMapper, Training> implements TrainingService {

    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final LessonMapper lessonMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTraining(TrainingCreateDTO dto) {
        validateTimeRange(dto.getEnrollStartTime(), dto.getEnrollEndTime(), dto.getStartTime(), dto.getEndTime());

        LocalDateTime now = LocalDateTime.now();
        Training training = new Training();
        training.setTitle(dto.getTitle());
        training.setDescription(dto.getDescription());
        training.setCoverUrl(dto.getCoverUrl());
        training.setTeacherId(dto.getTeacherId());
        training.setCapacity(dto.getCapacity());
        training.setEnrolledCount(0);
        training.setStatus(0);
        training.setEnrollStartTime(dto.getEnrollStartTime());
        training.setEnrollEndTime(dto.getEnrollEndTime());
        training.setStartTime(dto.getStartTime());
        training.setEndTime(dto.getEndTime());
        training.setCreateTime(now);
        training.setUpdateTime(now);

        save(training);
        log.info(
                "training created, trainingId={}, teacherId={}, title={}",
                training.getId(),
                training.getTeacherId(),
                training.getTitle()
        );
        return training.getId();
    }

    @Override
    public TrainingVO getTrainingDetail(Long id) {
        Training training = getExistingTraining(id);
        return convertToVO(training);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTraining(TrainingUpdateDTO dto) {
        Training training = getExistingTraining(dto.getId());
        validateTimeRange(dto.getEnrollStartTime(), dto.getEnrollEndTime(), dto.getStartTime(), dto.getEndTime());

        training.setTitle(dto.getTitle());
        training.setDescription(dto.getDescription());
        training.setCoverUrl(dto.getCoverUrl());
        training.setTeacherId(dto.getTeacherId());
        training.setCapacity(dto.getCapacity());
        training.setEnrollStartTime(dto.getEnrollStartTime());
        training.setEnrollEndTime(dto.getEnrollEndTime());
        training.setStartTime(dto.getStartTime());
        training.setEndTime(dto.getEndTime());
        training.setUpdateTime(LocalDateTime.now());

        updateById(training);
        log.info("training updated, trainingId={}", training.getId());
    }

    @Override
    public Page<TrainingVO> pageTrainings(TrainingPageQueryDTO dto) {
        //创建一个分页对象
        Page<Training> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<Training> queryWrapper = new LambdaQueryWrapper<Training>()
                .like(StringUtils.hasText(dto.getTitle()), Training::getTitle, dto.getTitle())
                .eq(dto.getStatus() != null, Training::getStatus, dto.getStatus())
                .eq(dto.getTeacherId() != null, Training::getTeacherId, dto.getTeacherId())
                .orderByDesc(Training::getCreateTime);

        Page<Training> trainingPage = page(page, queryWrapper);
        List<TrainingVO> records = trainingPage.getRecords().stream()
                .map(this::convertToVO)
                .toList();

        Page<TrainingVO> voPage = new Page<>(trainingPage.getCurrent(), trainingPage.getSize(), trainingPage.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    //修改培训的生命周期状态，状态只能一步一步向后流转
    //0：待发布 1：报名中  2：进行中  3：已结束
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer targetStatus) {
        if (targetStatus == null || targetStatus < 0 || targetStatus > 3) {
            throw new BusinessException("培训状态不合法");
        }

        Training training = getExistingTraining(id);
        Integer oldStatus = training.getStatus();
        if (oldStatus == null || targetStatus != oldStatus + 1) {
            log.warn(
                    "illegal training status transition, trainingId={}, oldStatus={}, targetStatus={}",
                    id,
                    oldStatus,
                    targetStatus
            );
            throw new BusinessException("非法状态流转");
        }

        training.setStatus(targetStatus);
        training.setUpdateTime(LocalDateTime.now());
        updateById(training);
        log.info(
                "training status changed, trainingId={}, oldStatus={}, newStatus={}",
                id,
                oldStatus,
                targetStatus
        );
    }

    @Override
    public TrainingCatalogVO getTrainingCatalog(Long trainingId) {
        Training training = getExistingTraining(trainingId);
        List<Course> courses = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getTrainingId, trainingId)
                .orderByAsc(Course::getSort)
                .orderByAsc(Course::getId));

        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        List<Chapter> chapters = courseIds.isEmpty()
                ? Collections.emptyList()
                : chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                        .in(Chapter::getCourseId, courseIds)
                        .orderByAsc(Chapter::getSort)
                        .orderByAsc(Chapter::getId));

        List<Long> chapterIds = chapters.stream().map(Chapter::getId).toList();
        List<Lesson> lessons = chapterIds.isEmpty()
                ? Collections.emptyList()
                : lessonMapper.selectList(new LambdaQueryWrapper<Lesson>()
                        .in(Lesson::getChapterId, chapterIds)
                        .orderByAsc(Lesson::getSort)
                        .orderByAsc(Lesson::getId));

        Map<Long, List<Lesson>> lessonMap = lessons.stream()
                .collect(Collectors.groupingBy(
                        Lesson::getChapterId,
                        Collectors.toList()
                ));

        Map<Long, List<Chapter>> chapterMap = chapters.stream()
                .collect(Collectors.groupingBy(
                        Chapter::getCourseId,
                        Collectors.toList()
                ));

        TrainingCatalogVO catalogVO = new TrainingCatalogVO();
        catalogVO.setTrainingId(training.getId());
        catalogVO.setTrainingTitle(training.getTitle());
        catalogVO.setCourses(courses.stream()
                .map(course -> {
                    CourseCatalogVO vo = convertToCourseCatalogVO(course);
                    vo.setChapters(chapterMap.getOrDefault(course.getId(), new ArrayList<>()).stream()
                            .map(chapter -> {
                                ChapterCatalogVO chapterVO = convertToChapterCatalogVO(chapter);
                                chapterVO.setLessons(lessonMap.getOrDefault(chapter.getId(), new ArrayList<>()).stream()
                                        .map(this::convertToLessonCatalogVO)
                                        .toList());
                                return chapterVO;
                            })
                            .toList());
                    return vo;
                })
                .toList());
        return catalogVO;
    }

    private Training getExistingTraining(Long id) {
        Training training = getById(id);
        if (training == null) {
            throw new BusinessException("培训不存在");
        }
        return training;
    }

    private void validateTimeRange(
            LocalDateTime enrollStartTime,
            LocalDateTime enrollEndTime,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        if (enrollStartTime != null && enrollEndTime != null && enrollEndTime.isBefore(enrollStartTime)) {
            throw new BusinessException("报名结束时间不能早于报名开始时间");
        }
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            throw new BusinessException("培训结束时间不能早于培训开始时间");
        }
    }

    private TrainingVO convertToVO(Training training) {
        TrainingVO vo = new TrainingVO();
        vo.setId(training.getId());
        vo.setTitle(training.getTitle());
        vo.setDescription(training.getDescription());
        vo.setCoverUrl(training.getCoverUrl());
        vo.setTeacherId(training.getTeacherId());
        vo.setCapacity(training.getCapacity());
        vo.setEnrolledCount(training.getEnrolledCount());
        vo.setStatus(training.getStatus());
        vo.setEnrollStartTime(training.getEnrollStartTime());
        vo.setEnrollEndTime(training.getEnrollEndTime());
        vo.setStartTime(training.getStartTime());
        vo.setEndTime(training.getEndTime());
        vo.setCreateTime(training.getCreateTime());
        vo.setUpdateTime(training.getUpdateTime());
        return vo;
    }

    private CourseCatalogVO convertToCourseCatalogVO(Course course) {
        CourseCatalogVO vo = new CourseCatalogVO();
        vo.setId(course.getId());
        vo.setTitle(course.getTitle());
        vo.setDescription(course.getDescription());
        vo.setSort(course.getSort());
        return vo;
    }

    private ChapterCatalogVO convertToChapterCatalogVO(Chapter chapter) {
        ChapterCatalogVO vo = new ChapterCatalogVO();
        vo.setId(chapter.getId());
        vo.setTitle(chapter.getTitle());
        vo.setSort(chapter.getSort());
        return vo;
    }

    private LessonCatalogVO convertToLessonCatalogVO(Lesson lesson) {
        LessonCatalogVO vo = new LessonCatalogVO();
        vo.setId(lesson.getId());
        vo.setTitle(lesson.getTitle());
        vo.setLessonType(lesson.getLessonType());
        vo.setSort(lesson.getSort());
        vo.setVideoObjectKey(lesson.getVideoObjectKey());
        vo.setVideoDuration(lesson.getVideoDuration());
        return vo;
    }
}

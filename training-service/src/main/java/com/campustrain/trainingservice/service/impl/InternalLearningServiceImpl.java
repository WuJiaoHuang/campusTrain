package com.campustrain.trainingservice.service.impl;

import com.campustrain.trainingservice.entity.Chapter;
import com.campustrain.trainingservice.entity.Course;
import com.campustrain.trainingservice.entity.Lesson;
import com.campustrain.trainingservice.exception.BusinessException;
import com.campustrain.trainingservice.mapper.ChapterMapper;
import com.campustrain.trainingservice.mapper.CourseMapper;
import com.campustrain.trainingservice.mapper.LessonMapper;
import com.campustrain.trainingservice.service.InternalLearningService;
import com.campustrain.trainingservice.service.LessonService;
import com.campustrain.trainingservice.vo.LessonInfoVO;
import com.campustrain.trainingservice.vo.VideoPlayVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InternalLearningServiceImpl implements InternalLearningService {
    private final LessonMapper lessonMapper;
    private final ChapterMapper chapterMapper;
    private final CourseMapper courseMapper;
    private final LessonService lessonService;

    @Override
    public LessonInfoVO getLessonInfo(Long lessonId) {
        Lesson lesson = lessonMapper.selectById(lessonId);
        if (lesson == null) throw new BusinessException("课时不存在");
        Chapter chapter = chapterMapper.selectById(lesson.getChapterId());
        if (chapter == null) throw new BusinessException("课时所属章节不存在");
        Course course = courseMapper.selectById(chapter.getCourseId());
        if (course == null) throw new BusinessException("课时所属课程不存在");
        return new LessonInfoVO(lessonId, lesson.getLessonType(), lesson.getVideoDuration(),
                chapter.getId(), course.getId(), course.getTrainingId(),
                StringUtils.hasText(lesson.getVideoObjectKey()));
    }

    @Override
    public VideoPlayVO getLessonPlayUrl(Long lessonId) {
        return lessonService.createPlayUrl(lessonId);
    }

    @Override
    public Long getTrainingIdByChapter(Long chapterId) {
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) throw new BusinessException("章节不存在");
        Course course = courseMapper.selectById(chapter.getCourseId());
        if (course == null) throw new BusinessException("章节所属课程不存在");
        return course.getTrainingId();
    }
}

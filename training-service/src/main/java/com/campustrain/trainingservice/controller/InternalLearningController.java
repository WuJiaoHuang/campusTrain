package com.campustrain.trainingservice.controller;

import com.campustrain.trainingservice.common.Result;
import com.campustrain.trainingservice.entity.Chapter;
import com.campustrain.trainingservice.entity.Course;
import com.campustrain.trainingservice.entity.Lesson;
import com.campustrain.trainingservice.exception.BusinessException;
import com.campustrain.trainingservice.mapper.ChapterMapper;
import com.campustrain.trainingservice.mapper.CourseMapper;
import com.campustrain.trainingservice.mapper.LessonMapper;
import com.campustrain.trainingservice.service.LessonService;
import com.campustrain.trainingservice.vo.VideoPlayVO;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/learning")
public class InternalLearningController {
    private final LessonMapper lessons;
    private final ChapterMapper chapters;
    private final CourseMapper courses;
    private final LessonService lessonService;

    public InternalLearningController(LessonMapper lessons, ChapterMapper chapters,
                                      CourseMapper courses, LessonService lessonService) {
        this.lessons = lessons;
        this.chapters = chapters;
        this.courses = courses;
        this.lessonService = lessonService;
    }

    public record LessonInfo(Long lessonId, Integer lessonType, Integer videoDuration,
                             Long chapterId, Long courseId, Long trainingId, boolean videoBound) {}

    @GetMapping("/lessons/{id}")
    public Result<LessonInfo> lesson(@PathVariable("id") Long id) {
        Lesson lesson = lessons.selectById(id);
        if (lesson == null) throw new BusinessException("课时不存在");
        Chapter chapter = chapters.selectById(lesson.getChapterId());
        if (chapter == null) throw new BusinessException("课时所属章节不存在");
        Course course = courses.selectById(chapter.getCourseId());
        if (course == null) throw new BusinessException("课时所属课程不存在");
        return Result.success(new LessonInfo(id, lesson.getLessonType(), lesson.getVideoDuration(),
                chapter.getId(), course.getId(), course.getTrainingId(),
                StringUtils.hasText(lesson.getVideoObjectKey())));
    }

    @GetMapping("/lessons/{id}/play-url")
    public Result<VideoPlayVO> playUrl(@PathVariable("id") Long id) {
        return Result.success(lessonService.createPlayUrl(id));
    }

    @GetMapping("/chapters/{id}/training")
    public Result<Long> chapterTraining(@PathVariable("id") Long id) {
        Chapter chapter = chapters.selectById(id);
        if (chapter == null) throw new BusinessException("章节不存在");
        Course course = courses.selectById(chapter.getCourseId());
        if (course == null) throw new BusinessException("章节所属课程不存在");
        return Result.success(course.getTrainingId());
    }
}

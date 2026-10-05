package com.campustrain.trainingservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campustrain.trainingservice.dto.LessonCreateDTO;
import com.campustrain.trainingservice.dto.LessonSortDTO;
import com.campustrain.trainingservice.dto.LessonUpdateDTO;
import com.campustrain.trainingservice.entity.Chapter;
import com.campustrain.trainingservice.entity.Lesson;
import com.campustrain.trainingservice.exception.BusinessException;
import com.campustrain.trainingservice.mapper.ChapterMapper;
import com.campustrain.trainingservice.mapper.LessonMapper;
import com.campustrain.trainingservice.service.LessonService;
import com.campustrain.trainingservice.vo.LessonVO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LessonServiceImpl extends ServiceImpl<LessonMapper, Lesson> implements LessonService {

    private final ChapterMapper chapterMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createLesson(LessonCreateDTO dto) {
        ensureChapterExists(dto.getChapterId());

        LocalDateTime now = LocalDateTime.now();
        Lesson lesson = new Lesson();
        lesson.setChapterId(dto.getChapterId());
        lesson.setTitle(dto.getTitle());
        lesson.setLessonType(dto.getLessonType());
        lesson.setSort(dto.getSort());
        lesson.setVideoObjectKey(dto.getVideoObjectKey());
        lesson.setVideoDuration(dto.getVideoDuration());
        lesson.setCreateTime(now);
        lesson.setUpdateTime(now);

        save(lesson);
        log.info("lesson created, lessonId={}, chapterId={}, title={}", lesson.getId(), lesson.getChapterId(), lesson.getTitle());
        return lesson.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLesson(LessonUpdateDTO dto) {
        Lesson lesson = getExistingLesson(dto.getId());
        lesson.setTitle(dto.getTitle());
        lesson.setLessonType(dto.getLessonType());
        lesson.setSort(dto.getSort());
        lesson.setVideoObjectKey(dto.getVideoObjectKey());
        lesson.setVideoDuration(dto.getVideoDuration());
        lesson.setUpdateTime(LocalDateTime.now());

        updateById(lesson);
        log.info("lesson updated, lessonId={}", lesson.getId());
    }

    @Override
    public List<LessonVO> listByChapterId(Long chapterId) {
        ensureChapterExists(chapterId);
        return list(new LambdaQueryWrapper<Lesson>()
                .eq(Lesson::getChapterId, chapterId)
                .orderByAsc(Lesson::getSort)
                .orderByAsc(Lesson::getId))
                .stream()
                .map(this::convertToVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sortLessons(List<LessonSortDTO> dtoList) {
        LocalDateTime now = LocalDateTime.now();
        for (LessonSortDTO dto : dtoList) {
            Lesson lesson = getExistingLesson(dto.getId());
            lesson.setSort(dto.getSort());
            lesson.setUpdateTime(now);
            updateById(lesson);
        }
        log.info("lessons sorted, count={}", dtoList.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLesson(Long id) {
        Lesson lesson = getExistingLesson(id);
        removeById(id);
        log.info("lesson deleted, lessonId={}", lesson.getId());
    }

    private void ensureChapterExists(Long chapterId) {
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException("章节不存在");
        }
    }

    private Lesson getExistingLesson(Long id) {
        Lesson lesson = getById(id);
        if (lesson == null) {
            throw new BusinessException("课时不存在");
        }
        return lesson;
    }

    private LessonVO convertToVO(Lesson lesson) {
        LessonVO vo = new LessonVO();
        vo.setId(lesson.getId());
        vo.setChapterId(lesson.getChapterId());
        vo.setTitle(lesson.getTitle());
        vo.setLessonType(lesson.getLessonType());
        vo.setSort(lesson.getSort());
        vo.setVideoObjectKey(lesson.getVideoObjectKey());
        vo.setVideoDuration(lesson.getVideoDuration());
        vo.setCreateTime(lesson.getCreateTime());
        vo.setUpdateTime(lesson.getUpdateTime());
        return vo;
    }
}

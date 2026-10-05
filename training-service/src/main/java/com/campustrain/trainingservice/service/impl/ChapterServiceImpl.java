package com.campustrain.trainingservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campustrain.trainingservice.dto.ChapterCreateDTO;
import com.campustrain.trainingservice.dto.ChapterSortDTO;
import com.campustrain.trainingservice.dto.ChapterUpdateDTO;
import com.campustrain.trainingservice.entity.Chapter;
import com.campustrain.trainingservice.entity.Course;
import com.campustrain.trainingservice.entity.Lesson;
import com.campustrain.trainingservice.exception.BusinessException;
import com.campustrain.trainingservice.mapper.ChapterMapper;
import com.campustrain.trainingservice.mapper.CourseMapper;
import com.campustrain.trainingservice.mapper.LessonMapper;
import com.campustrain.trainingservice.service.ChapterService;
import com.campustrain.trainingservice.vo.ChapterVO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterServiceImpl extends ServiceImpl<ChapterMapper, Chapter> implements ChapterService {

    private final CourseMapper courseMapper;
    private final LessonMapper lessonMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createChapter(ChapterCreateDTO dto) {
        ensureCourseExists(dto.getCourseId());

        LocalDateTime now = LocalDateTime.now();
        Chapter chapter = new Chapter();
        chapter.setCourseId(dto.getCourseId());
        chapter.setTitle(dto.getTitle());
        chapter.setSort(dto.getSort());
        chapter.setCreateTime(now);
        chapter.setUpdateTime(now);

        save(chapter);
        log.info("chapter created, chapterId={}, courseId={}, title={}", chapter.getId(), chapter.getCourseId(), chapter.getTitle());
        return chapter.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateChapter(ChapterUpdateDTO dto) {
        Chapter chapter = getExistingChapter(dto.getId());
        chapter.setTitle(dto.getTitle());
        chapter.setSort(dto.getSort());
        chapter.setUpdateTime(LocalDateTime.now());

        updateById(chapter);
        log.info("chapter updated, chapterId={}", chapter.getId());
    }

    @Override
    public List<ChapterVO> listByCourseId(Long courseId) {
        ensureCourseExists(courseId);
        return list(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getCourseId, courseId)
                .orderByAsc(Chapter::getSort)
                .orderByAsc(Chapter::getId))
                .stream()
                .map(this::convertToVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sortChapters(List<ChapterSortDTO> dtoList) {
        LocalDateTime now = LocalDateTime.now();
        for (ChapterSortDTO dto : dtoList) {
            Chapter chapter = getExistingChapter(dto.getId());
            chapter.setSort(dto.getSort());
            chapter.setUpdateTime(now);
            updateById(chapter);
        }
        log.info("chapters sorted, count={}", dtoList.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChapter(Long id) {
        Chapter chapter = getExistingChapter(id);
        Long lessonCount = lessonMapper.selectCount(new LambdaQueryWrapper<Lesson>().eq(Lesson::getChapterId, id));
        if (lessonCount > 0) {
            log.warn("chapter delete rejected, chapterId={}, lessonCount={}", id, lessonCount);
            throw new BusinessException("章节下存在课时，无法删除");
        }

        removeById(id);
        log.info("chapter deleted, chapterId={}", chapter.getId());
    }

    private void ensureCourseExists(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
    }

    private Chapter getExistingChapter(Long id) {
        Chapter chapter = getById(id);
        if (chapter == null) {
            throw new BusinessException("章节不存在");
        }
        return chapter;
    }

    private ChapterVO convertToVO(Chapter chapter) {
        ChapterVO vo = new ChapterVO();
        vo.setId(chapter.getId());
        vo.setCourseId(chapter.getCourseId());
        vo.setTitle(chapter.getTitle());
        vo.setSort(chapter.getSort());
        vo.setCreateTime(chapter.getCreateTime());
        vo.setUpdateTime(chapter.getUpdateTime());
        return vo;
    }
}

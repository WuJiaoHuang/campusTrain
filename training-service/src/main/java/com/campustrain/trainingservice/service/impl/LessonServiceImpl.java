package com.campustrain.trainingservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campustrain.trainingservice.dto.LessonCreateDTO;
import com.campustrain.trainingservice.dto.LessonSortDTO;
import com.campustrain.trainingservice.dto.LessonUpdateDTO;
import com.campustrain.trainingservice.dto.VideoUploadDTO;
import com.campustrain.trainingservice.dto.VideoBindDTO;
import com.campustrain.trainingservice.config.OssProperties;
import com.campustrain.trainingservice.context.TraceIdContext;
import com.campustrain.trainingservice.service.OssService;
import com.campustrain.trainingservice.vo.VideoPlayVO;
import com.campustrain.trainingservice.vo.VideoUploadVO;
import com.aliyun.oss.model.ObjectMetadata;
import com.campustrain.trainingservice.entity.Chapter;
import com.campustrain.trainingservice.entity.Lesson;
import com.campustrain.trainingservice.exception.BusinessException;
import com.campustrain.trainingservice.mapper.ChapterMapper;
import com.campustrain.trainingservice.mapper.LessonMapper;
import com.campustrain.trainingservice.service.LessonService;
import com.campustrain.trainingservice.vo.LessonVO;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class LessonServiceImpl extends ServiceImpl<LessonMapper, Lesson> implements LessonService {

    private final ChapterMapper chapterMapper;
    private final OssService ossService;
    private final OssProperties ossProperties;
    private static final Map<String, String> VIDEO_TYPES = Map.of(
            "video/mp4", "mp4", "video/webm", "webm", "video/quicktime", "mov");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createLesson(LessonCreateDTO dto) {
        rejectLegacyVideoFields(dto.getVideoObjectKey(), dto.getVideoDuration());
        ensureChapterExists(dto.getChapterId());

        LocalDateTime now = LocalDateTime.now();
        Lesson lesson = new Lesson();
        lesson.setChapterId(dto.getChapterId());
        lesson.setTitle(dto.getTitle());
        lesson.setLessonType(dto.getLessonType());
        lesson.setSort(dto.getSort());
        lesson.setCreateTime(now);
        lesson.setUpdateTime(now);

        save(lesson);
        log.info("lesson created, lessonId={}, chapterId={}, title={}", lesson.getId(), lesson.getChapterId(), lesson.getTitle());
        return lesson.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLesson(LessonUpdateDTO dto) {
        rejectLegacyVideoFields(dto.getVideoObjectKey(), dto.getVideoDuration());
        Lesson lesson = getExistingLesson(dto.getId());
        if (!Integer.valueOf(0).equals(dto.getLessonType()) && StringUtils.hasText(lesson.getVideoObjectKey())) {
            throw new BusinessException("已绑定视频的课时不能修改为图文课时");
        }
        lesson.setTitle(dto.getTitle());
        lesson.setLessonType(dto.getLessonType());
        lesson.setSort(dto.getSort());
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
        if (!removeById(id)) throw new BusinessException("删除课时失败");
        cleanupAfterCommit(lesson.getId(), lesson.getVideoObjectKey());
        log.info("lesson deleted, lessonId={}", lesson.getId());
    }

    @Override
    public VideoUploadVO createUploadUrl(VideoUploadDTO dto) {
        validateVideo(dto.getSize(), dto.getContentType());
        if (!StringUtils.hasText(dto.getFileName()) || dto.getFileName().contains("/") || dto.getFileName().contains("\\")) {
            throw new BusinessException("文件名无效");
        }
        String extension = VIDEO_TYPES.get(dto.getContentType());
        if (!dto.getFileName().toLowerCase(java.util.Locale.ROOT).endsWith("." + extension)) {
            throw new BusinessException("文件扩展名与视频类型不匹配");
        }
        String key = "campustrain/videos/" + YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy/MM"))
                + "/" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Instant expiry = Instant.now().plusSeconds(ossProperties.getUploadExpireSeconds());
        String url = ossService.generateUploadUrl(key, dto.getContentType(), expiry);
        log.info("upload URL generated, traceId={}, objectKey={}, size={}, contentType={}", TraceIdContext.get(), key, dto.getSize(), dto.getContentType());
        return new VideoUploadVO(key, url, expiry);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindVideo(Long id, VideoBindDTO dto) {
        Lesson lesson = getOne(new LambdaQueryWrapper<Lesson>().eq(Lesson::getId, id).last("FOR UPDATE"));
        if (lesson == null) throw new BusinessException("课时不存在");
        if (!Integer.valueOf(0).equals(lesson.getLessonType())) throw new BusinessException("非视频课时不能绑定视频");
        if (dto.getObjectKey() == null || !dto.getObjectKey().matches("campustrain/videos/[0-9]{4}/[0-9]{2}/[a-f0-9]{32}\\.(mp4|webm|mov)")) {
            throw new BusinessException("视频对象Key无效");
        }
        validateVideo(dto.getSize(), dto.getContentType());
        if (dto.getDuration() == null || dto.getDuration() < 0) throw new BusinessException("视频时长不能小于0");
        if (!dto.getObjectKey().endsWith("." + VIDEO_TYPES.get(dto.getContentType()))) throw new BusinessException("视频类型与扩展名不匹配");
        if (!ossService.doesObjectExist(dto.getObjectKey())) throw new BusinessException("OSS视频对象不存在");
        ObjectMetadata metadata = ossService.getObjectMetadata(dto.getObjectKey());
        if (metadata.getContentLength() != dto.getSize()) throw new BusinessException("视频大小与OSS对象不一致");
        if (!dto.getContentType().equals(metadata.getContentType())) throw new BusinessException("视频类型与OSS对象不一致");
        String oldKey = lesson.getVideoObjectKey();
        lesson.setVideoObjectKey(dto.getObjectKey());
        lesson.setVideoSize(metadata.getContentLength());
        lesson.setVideoContentType(metadata.getContentType());
        lesson.setVideoDuration(dto.getDuration());
        lesson.setUpdateTime(LocalDateTime.now());
        if (!updateById(lesson)) throw new BusinessException("视频绑定失败");
        if (!dto.getObjectKey().equals(oldKey)) cleanupAfterCommit(id, oldKey);
        log.info("video bound, traceId={}, lessonId={}, objectKey={}, size={}, contentType={}", TraceIdContext.get(), id, dto.getObjectKey(), dto.getSize(), dto.getContentType());
    }

    @Override
    public VideoPlayVO createPlayUrl(Long id) {
        Lesson lesson = getExistingLesson(id);
        if (!Integer.valueOf(0).equals(lesson.getLessonType())) throw new BusinessException("非视频课时不能播放视频");
        if (!StringUtils.hasText(lesson.getVideoObjectKey())) throw new BusinessException("课时未绑定视频");
        Instant expiry = Instant.now().plusSeconds(ossProperties.getPlayExpireSeconds());
        return new VideoPlayVO(ossService.generatePlayUrl(lesson.getVideoObjectKey(), expiry), expiry);
    }

    private void validateVideo(Long size, String contentType) {
        if (!VIDEO_TYPES.containsKey(contentType)) throw new BusinessException("不支持的视频类型");
        if (size == null || size <= 0 || size > ossProperties.getMaxVideoSize()) throw new BusinessException("视频大小无效或超过上限");
    }

    private void rejectLegacyVideoFields(String key, Integer duration) {
        if (key != null || duration != null) throw new BusinessException("请通过视频绑定接口设置视频");
    }

    private void cleanupAfterCommit(Long lessonId, String key) {
        if (!StringUtils.hasText(key)) return;
        String traceId = TraceIdContext.get();
        Runnable cleanup = () -> {
            try {
                ossService.deleteObject(key);
                log.info("OSS object deleted, traceId={}, lessonId={}, objectKey={}", traceId, lessonId, key);
            } catch (Exception e) {
                log.error("OSS cleanup failed, traceId={}, lessonId={}, objectKey={}", traceId, lessonId, key, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { cleanup.run(); }
            });
        } else {
            cleanup.run();
        }
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
        vo.setVideoSize(lesson.getVideoSize());
        vo.setVideoContentType(lesson.getVideoContentType());
        vo.setCreateTime(lesson.getCreateTime());
        vo.setUpdateTime(lesson.getUpdateTime());
        return vo;
    }
}

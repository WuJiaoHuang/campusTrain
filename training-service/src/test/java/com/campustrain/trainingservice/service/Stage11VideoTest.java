package com.campustrain.trainingservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aliyun.oss.model.ObjectMetadata;
import com.campustrain.trainingservice.config.OssProperties;
import com.campustrain.trainingservice.dto.LessonCreateDTO;
import com.campustrain.trainingservice.dto.VideoBindDTO;
import com.campustrain.trainingservice.dto.VideoUploadDTO;
import com.campustrain.trainingservice.entity.Lesson;
import com.campustrain.trainingservice.exception.BusinessException;
import com.campustrain.trainingservice.mapper.ChapterMapper;
import com.campustrain.trainingservice.mapper.CourseMapper;
import com.campustrain.trainingservice.cache.TrainingCacheService;
import com.campustrain.trainingservice.service.impl.LessonServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class Stage11VideoTest {
    private static final String KEY = "campustrain/videos/2026/10/0123456789abcdef0123456789abcdef.mp4";
    private static final String OLD = "campustrain/videos/2026/10/abcdef0123456789abcdef0123456789.mp4";
    private OssService oss;
    private LessonServiceImpl service;
    private Lesson lesson;
    private VideoBindDTO bind;

    @BeforeEach
    void setup() {
        oss = mock(OssService.class);
        service = spy(new LessonServiceImpl(mock(ChapterMapper.class), mock(CourseMapper.class), oss,
                new OssProperties(), mock(TrainingCacheService.class)));
        lesson = new Lesson();
        lesson.setId(1L);
        lesson.setLessonType(0);
        bind = new VideoBindDTO();
        bind.setObjectKey(KEY);
        bind.setSize(123L);
        bind.setContentType("video/mp4");
        bind.setDuration(10);
        doReturn(lesson).when(service).getOne(any());
        doReturn(lesson).when(service).getById(anyLong());
        doReturn(true).when(service).updateById(any(Lesson.class));
        doReturn(true).when(service).removeById(anyLong());
        when(oss.doesObjectExist(KEY)).thenReturn(true);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(123);
        metadata.setContentType("video/mp4");
        when(oss.getObjectMetadata(KEY)).thenReturn(metadata);
    }

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void validatesUploadTypeSizeAndFilename() {
        VideoUploadDTO dto = new VideoUploadDTO();
        dto.setFileName("x.mp4");
        dto.setContentType("image/png");
        dto.setSize(10L);
        assertThrows(BusinessException.class, () -> service.createUploadUrl(dto));
        dto.setContentType("video/mp4");
        dto.setSize(0L);
        assertThrows(BusinessException.class, () -> service.createUploadUrl(dto));
        dto.setSize(524288001L);
        assertThrows(BusinessException.class, () -> service.createUploadUrl(dto));
        dto.setSize(10L);
        dto.setFileName("../x.mp4");
        assertThrows(BusinessException.class, () -> service.createUploadUrl(dto));
        verifyNoInteractions(oss);
    }

    @Test
    void uploadUsesServerGeneratedKeyAndPresignedUrl() {
        VideoUploadDTO dto = new VideoUploadDTO();
        dto.setFileName("private-source.mp4");
        dto.setContentType("video/mp4");
        dto.setSize(100L);
        when(oss.generateUploadUrl(anyString(), eq("video/mp4"), any())).thenReturn("signed-put");
        var result = service.createUploadUrl(dto);
        assertTrue(result.objectKey().matches("campustrain/videos/[0-9]{4}/[0-9]{2}/[a-f0-9]{32}\\.mp4"));
        assertFalse(result.objectKey().contains("private-source"));
        assertEquals("signed-put", result.uploadUrl());
    }

    @Test
    void rejectsLegacyCrudVideo() {
        LessonCreateDTO dto = new LessonCreateDTO();
        dto.setVideoObjectKey(KEY);
        assertThrows(BusinessException.class, () -> service.createLesson(dto));
    }

    @Test
    void rejectsMissingLessonNonVideoAndInvalidKey() {
        doReturn(null).when(service).getOne(any());
        assertThrows(BusinessException.class, () -> service.bindVideo(1L, bind));
        doReturn(lesson).when(service).getOne(any());
        lesson.setLessonType(1);
        assertThrows(BusinessException.class, () -> service.bindVideo(1L, bind));
        lesson.setLessonType(0);
        bind.setObjectKey("other/path.mp4");
        assertThrows(BusinessException.class, () -> service.bindVideo(1L, bind));
        verifyNoInteractions(oss);
    }

    @Test
    void rejectsMissingObjectAndMismatchedMetadata() {
        when(oss.doesObjectExist(KEY)).thenReturn(false, true);
        assertThrows(BusinessException.class, () -> service.bindVideo(1L, bind));
        bind.setSize(124L);
        assertThrows(BusinessException.class, () -> service.bindVideo(1L, bind));
        verify(service, never()).updateById(any(Lesson.class));
    }

    @Test
    void rejectsWrongActualContentType() {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(123);
        metadata.setContentType("image/png");
        when(oss.getObjectMetadata(KEY)).thenReturn(metadata);
        assertThrows(BusinessException.class, () -> service.bindVideo(1L, bind));
        verify(service, never()).updateById(any(Lesson.class));
    }

    @Test
    void bindsMetadataAndCleansOldOnlyAfterCommit() {
        lesson.setVideoObjectKey(OLD);
        TransactionSynchronizationManager.initSynchronization();
        service.bindVideo(1L, bind);
        assertEquals(KEY, lesson.getVideoObjectKey());
        assertEquals(123L, lesson.getVideoSize());
        assertEquals("video/mp4", lesson.getVideoContentType());
        assertEquals(10, lesson.getVideoDuration());
        verify(oss, never()).deleteObject(anyString());
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
        InOrder order = inOrder(service, oss);
        order.verify(service).updateById(lesson);
        order.verify(oss).deleteObject(OLD);
    }

    @Test
    void playUrlRequiresBoundVideo() {
        assertThrows(BusinessException.class, () -> service.createPlayUrl(1L));
        lesson.setVideoObjectKey(KEY);
        when(oss.generatePlayUrl(eq(KEY), any())).thenReturn("signed");
        assertEquals("signed", service.createPlayUrl(1L).playUrl());
    }

    @Test
    void cleanupFailuresDoNotUndoBindingOrDeletion() {
        lesson.setVideoObjectKey(OLD);
        doThrow(new IllegalStateException("cleanup")).when(oss).deleteObject(OLD);
        assertDoesNotThrow(() -> service.bindVideo(1L, bind));
        assertEquals(KEY, lesson.getVideoObjectKey());
        doThrow(new IllegalStateException("cleanup")).when(oss).deleteObject(KEY);
        assertDoesNotThrow(() -> service.deleteLesson(1L));
        verify(service).removeById(1L);
        verify(oss).deleteObject(KEY);
    }
}

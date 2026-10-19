package com.campustrain.trainingservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campustrain.trainingservice.dto.LessonCreateDTO;
import com.campustrain.trainingservice.dto.LessonSortDTO;
import com.campustrain.trainingservice.dto.LessonUpdateDTO;
import com.campustrain.trainingservice.dto.VideoBindDTO;
import com.campustrain.trainingservice.dto.VideoUploadDTO;
import com.campustrain.trainingservice.vo.VideoPlayVO;
import com.campustrain.trainingservice.vo.VideoUploadVO;
import com.campustrain.trainingservice.entity.Lesson;
import com.campustrain.trainingservice.vo.LessonVO;
import java.util.List;

public interface LessonService extends IService<Lesson> {

    Long createLesson(LessonCreateDTO dto);

    void updateLesson(LessonUpdateDTO dto);

    List<LessonVO> listByChapterId(Long chapterId);

    void sortLessons(List<LessonSortDTO> dtoList);

    void deleteLesson(Long id);

    VideoUploadVO createUploadUrl(VideoUploadDTO dto);

    void bindVideo(Long id, VideoBindDTO dto);

    VideoPlayVO createPlayUrl(Long id);
}

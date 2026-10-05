package com.campustrain.trainingservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campustrain.trainingservice.dto.ChapterCreateDTO;
import com.campustrain.trainingservice.dto.ChapterSortDTO;
import com.campustrain.trainingservice.dto.ChapterUpdateDTO;
import com.campustrain.trainingservice.entity.Chapter;
import com.campustrain.trainingservice.vo.ChapterVO;
import java.util.List;

public interface ChapterService extends IService<Chapter> {

    Long createChapter(ChapterCreateDTO dto);

    void updateChapter(ChapterUpdateDTO dto);

    List<ChapterVO> listByCourseId(Long courseId);

    void sortChapters(List<ChapterSortDTO> dtoList);

    void deleteChapter(Long id);
}

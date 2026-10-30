package com.campustrain.communityservice.vo;

import java.time.LocalDateTime;
import java.util.List;

public record QuestionView(Long id, Long trainingId, Long courseId, Long lessonId, Long userId,
                           String title, String content, int answerCount, long likeCount,
                           boolean likedByMe, LocalDateTime createTime, List<AnswerView> answers,
                           List<CommentView> comments) { }

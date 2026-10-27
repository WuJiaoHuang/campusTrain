package com.campustrain.learningservice.client.dto;

import java.util.List;

public record Chapter(Long id, List<LessonNode> lessons) {
}

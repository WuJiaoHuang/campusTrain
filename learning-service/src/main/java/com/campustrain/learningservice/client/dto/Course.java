package com.campustrain.learningservice.client.dto;

import java.util.List;

public record Course(Long id, List<Chapter> chapters) {
}

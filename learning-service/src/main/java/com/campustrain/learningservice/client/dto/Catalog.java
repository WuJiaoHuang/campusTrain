package com.campustrain.learningservice.client.dto;

import java.util.List;

public record Catalog(Long trainingId, List<Course> courses) {
}

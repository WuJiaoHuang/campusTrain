package com.campustrain.communityservice.client.dto;

import java.util.List;

public record Catalog(Long trainingId, List<CourseRef> courses) { }

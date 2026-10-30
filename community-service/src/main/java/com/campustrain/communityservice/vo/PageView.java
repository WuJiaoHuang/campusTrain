package com.campustrain.communityservice.vo;

import java.util.List;

public record PageView<T>(long total, int page, int size, List<T> records) { }

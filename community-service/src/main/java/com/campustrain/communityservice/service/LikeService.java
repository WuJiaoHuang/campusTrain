package com.campustrain.communityservice.service;

import com.campustrain.communityservice.exception.CommunityException;
import com.campustrain.communityservice.vo.LikeView;
import java.util.Map;

public interface LikeService {
    LikeView like(String type, long targetId);
    LikeView unlike(String type, long targetId);
    LikeView read(String type, long targetId, long persistedCount);
    LikeView readCurrent(String type, long targetId);
    Map<Long, LikeView> readBatch(String type, Map<Long, Long> persistedCounts);

    static String targetType(String value) {
        if ("QUESTION".equals(value) || "ANSWER".equals(value)) return value;
        throw new CommunityException(400, "不支持的目标类型");
    }
}

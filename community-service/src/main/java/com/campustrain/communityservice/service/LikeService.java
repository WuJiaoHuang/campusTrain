package com.campustrain.communityservice.service;

import com.campustrain.communityservice.exception.CommunityException;
import com.campustrain.communityservice.vo.LikeView;
import java.util.Map;

public interface LikeService {
    LikeView likeTarget(String targetType, long targetId);
    LikeView unlikeTarget(String targetType, long targetId);
    LikeView getLikeState(String targetType, long targetId, long persistedCount);
    LikeView getLikeState(String targetType, long targetId);
    Map<Long, LikeView> getLikeStates(String targetType, Map<Long, Long> persistedCounts);

    static String normalizeTargetType(String value) {
        if ("QUESTION".equals(value) || "ANSWER".equals(value)) return value;
        throw new CommunityException(400, "不支持的目标类型");
    }
}

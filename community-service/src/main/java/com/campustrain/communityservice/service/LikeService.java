package com.campustrain.communityservice.service;

import com.campustrain.communityservice.exception.CommunityException;
import com.campustrain.communityservice.vo.LikeView;
import java.util.Map;

public interface LikeService {
    LikeView likeTarget(String type, long targetId);
    LikeView unlikeTarget(String type, long targetId);
    LikeView getLikeState(String type, long targetId, long persistedCount);
    LikeView getLikeState(String type, long targetId);
    Map<Long, LikeView> getLikeStates(String type, Map<Long, Long> persistedCounts);

    static String targetType(String value) {
        if ("QUESTION".equals(value) || "ANSWER".equals(value)) return value;
        throw new CommunityException(400, "不支持的目标类型");
    }
}

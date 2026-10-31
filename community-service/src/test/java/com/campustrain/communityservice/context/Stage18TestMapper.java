package com.campustrain.communityservice.context;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface Stage18TestMapper {
    @Select("SELECT COUNT(*) FROM like_relation WHERE target_type='QUESTION' AND target_id=#{targetId}")
    long countAllLikes(@Param("targetId") long targetId);

    @Select("SELECT COUNT(*) FROM like_relation WHERE target_type='QUESTION' AND target_id=#{targetId} AND status=1")
    long countActiveLikes(@Param("targetId") long targetId);

    @Select("SELECT like_count FROM question WHERE id=#{targetId}")
    long selectQuestionLikeCount(@Param("targetId") long targetId);

    @Delete("DELETE FROM like_relation WHERE target_type='QUESTION' AND target_id=#{targetId}")
    int deleteLikes(@Param("targetId") long targetId);

    @Delete("DELETE FROM question WHERE id=#{targetId} AND title='stage18-live-test'")
    int deleteTestQuestion(@Param("targetId") long targetId);
}

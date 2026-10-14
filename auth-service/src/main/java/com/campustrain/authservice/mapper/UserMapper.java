package com.campustrain.authservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campustrain.authservice.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}

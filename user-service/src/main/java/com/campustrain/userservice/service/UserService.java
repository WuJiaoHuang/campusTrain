package com.campustrain.userservice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campustrain.userservice.dto.UserCreateDTO;
import com.campustrain.userservice.dto.UserPageQueryDTO;
import com.campustrain.userservice.dto.UserUpdateDTO;
import com.campustrain.userservice.entity.User;
import com.campustrain.userservice.vo.UserVO;

public interface UserService extends IService<User> {

    Long createUser(UserCreateDTO dto);

    UserVO getUserDetail(Long id);

    Page<UserVO> pageUsers(UserPageQueryDTO dto);

    void updateUser(UserUpdateDTO dto);

    void deleteUser(Long id);

    User getUserEntityByUsername(String username);
}

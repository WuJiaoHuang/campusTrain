package com.campustrain.userservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campustrain.userservice.dto.UserCreateDTO;
import com.campustrain.userservice.dto.UserPageQueryDTO;
import com.campustrain.userservice.dto.UserUpdateDTO;
import com.campustrain.userservice.entity.User;
import com.campustrain.userservice.exception.BusinessException;
import com.campustrain.userservice.mapper.UserMapper;
import com.campustrain.userservice.service.UserService;
import com.campustrain.userservice.vo.UserVO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUser(UserCreateDTO dto) {
        Long sameUsernameCount = count(new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (sameUsernameCount > 0) {
            log.warn("user create rejected, username already exists, username={}", dto.getUsername());
            throw new BusinessException("用户名已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setNickname(dto.getNickname());
        user.setRole(dto.getRole());
        user.setStatus(dto.getStatus());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setCreateTime(now);
        user.setUpdateTime(now);

        save(user);
        log.info(
                "user created, userId={}, username={}, role={}",
                user.getId(),
                user.getUsername(),
                user.getRole()
        );
        return user.getId();
    }

    @Override
    public UserVO getUserDetail(Long id) {
        return convertToVO(getExistingUser(id));
    }

    @Override
    public Page<UserVO> pageUsers(UserPageQueryDTO dto) {
        Page<User> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .like(StringUtils.hasText(dto.getUsername()), User::getUsername, dto.getUsername())
                .like(StringUtils.hasText(dto.getNickname()), User::getNickname, dto.getNickname())
                .eq(StringUtils.hasText(dto.getRole()), User::getRole, dto.getRole())
                .eq(dto.getStatus() != null, User::getStatus, dto.getStatus())
                .orderByDesc(User::getCreateTime);

        Page<User> userPage = page(page, queryWrapper);
        List<UserVO> records = userPage.getRecords().stream()
                .map(this::convertToVO)
                .toList();

        Page<UserVO> voPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    //只要抛出Exception或它的子类，就回滚事务
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(UserUpdateDTO dto) {
        User user = getExistingUser(dto.getId());
        user.setNickname(dto.getNickname());
        user.setRole(dto.getRole());
        user.setStatus(dto.getStatus());
        user.setUpdateTime(LocalDateTime.now());

        updateById(user);
        log.info(
                "user updated, userId={}, role={}, status={}",
                user.getId(),
                user.getRole(),
                user.getStatus()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        getExistingUser(id);
        removeById(id);
        log.info("user deleted, userId={}", id);
    }

    @Override
    public User getUserEntityByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username), false);
    }

    private User getExistingUser(Long id) {
        User user = getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private UserVO convertToVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }
}

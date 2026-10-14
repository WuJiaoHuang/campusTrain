package com.campustrain.userservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campustrain.userservice.common.Result;
import com.campustrain.userservice.dto.UserCreateDTO;
import com.campustrain.userservice.dto.UserPageQueryDTO;
import com.campustrain.userservice.dto.UserUpdateDTO;
import com.campustrain.userservice.service.UserService;
import com.campustrain.userservice.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
@Tag(name = "用户管理")
public class UserController {

    private final UserService userService;

    @Operation(summary = "创建用户")
    @PostMapping
    public Result<Long> createUser(@Valid @RequestBody UserCreateDTO dto) {
        return Result.success(userService.createUser(dto));
    }

    @Operation(summary = "查询用户详情")
    @GetMapping("/{id}")
    public Result<UserVO> getUserDetail(@PathVariable("id") Long id) {
        return Result.success(userService.getUserDetail(id));
    }

    @Operation(summary = "分页查询用户")
    @GetMapping("/page")
    public Result<Page<UserVO>> pageUsers(@Valid UserPageQueryDTO dto) {
        return Result.success(userService.pageUsers(dto));
    }

    @Operation(summary = "修改用户")
    @PutMapping
    public Result<Void> updateUser(@Valid @RequestBody UserUpdateDTO dto) {
        userService.updateUser(dto);
        return Result.success();
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable("id") Long id) {
        userService.deleteUser(id);
        return Result.success();
    }
}

package com.campustrain.userservice.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    private String role;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

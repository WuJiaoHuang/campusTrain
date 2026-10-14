package com.campustrain.authservice.vo;

import lombok.Data;

@Data
public class LoginVO {

    private String token;

    private String tokenType;

    private Long expiresIn;

    private Long userId;

    private String username;

    private String role;
}

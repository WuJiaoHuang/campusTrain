package com.campustrain.authservice.vo;

import lombok.Data;

@Data
public class TokenInfoVO {

    private Long userId;

    private String username;

    private String role;
}

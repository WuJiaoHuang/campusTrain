package com.campustrain.enrollmentservice.remote.vo;

import lombok.Data;

@Data
public class RemoteUserVO {

    private Long id;

    private String username;

    private String role;

    private Integer status;
}

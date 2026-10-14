package com.campustrain.userservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UserPageQueryDTO {

    @Min(value = 1, message = "页码不能小于1")
    private Long pageNum = 1L;

    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能大于100")
    private Long pageSize = 10L;

    private String username;

    private String nickname;

    @Pattern(regexp = "STUDENT|TEACHER|ADMIN", message = "角色只能是STUDENT、TEACHER或ADMIN")
    private String role;

    @Min(value = 0, message = "状态只能是0或1")
    @Max(value = 1, message = "状态只能是0或1")
    private Integer status;
}

package com.campustrain.authservice.service;

import com.campustrain.authservice.dto.LoginDTO;
import com.campustrain.authservice.vo.LoginVO;

public interface AuthService {

    LoginVO login(LoginDTO dto);
}

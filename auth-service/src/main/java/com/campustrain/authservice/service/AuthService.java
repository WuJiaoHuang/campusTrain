package com.campustrain.authservice.service;

import com.campustrain.authservice.dto.LoginDTO;
import com.campustrain.authservice.vo.LoginVO;
import com.campustrain.authservice.vo.TokenInfoVO;

public interface AuthService {

    LoginVO login(LoginDTO dto);

    TokenInfoVO getTokenInfo(String authorization);
}

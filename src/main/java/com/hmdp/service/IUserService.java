package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpSession;

public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginFormDTO, HttpSession session);

    Result loginV2(LoginFormDTO loginFormDTO, HttpSession session);

    Result refreshToken(String refreshToken);

    Result logout(String authorizationHeader, String refreshTokenHeader);

    Result sign();

    Result signCount();
}

package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //实现将当前用户放到threadlocal里
        //获取当前session
        HttpSession session = request.getSession();
        //从session里获取当前用户
        Object user = session.getAttribute("user");
        log.info("拦截器启动拦截");
        //不存在用户
        if (user == null) {
            response.setStatus(401);
            return false;
        }
        //不知道为什么用的是dto先拷贝一下
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        UserHolder.saveUser(userDTO);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
       UserHolder.removeUser();
    }
}

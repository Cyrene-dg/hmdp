package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private final StringRedisTemplate stringRedisTemplate;
    private final String authMode;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate, String authMode) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.authMode = authMode;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = AuthTokenUtil.extractToken(request.getHeader("Authorization"));
        if (StrUtil.isBlank(token)) {
            return true;
        }

        if (supportsV2() && tryLoadAndBindUser(RedisConstants.LOGIN_ACCESS_KEY + token, RedisConstants.LOGIN_ACCESS_TTL)) {
            return true;
        }

        if (supportsLegacy() && tryLoadAndBindUser(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL)) {
            return true;
        }

        return true;
    }

    private boolean tryLoadAndBindUser(String key, Long ttlMinutes) {
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if (userMap == null || userMap.isEmpty()) {
            return false;
        }
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        stringRedisTemplate.expire(key, ttlMinutes, TimeUnit.MINUTES);
        return true;
    }

    private boolean supportsV2() {
        return !RedisConstants.AUTH_MODE_LEGACY.equalsIgnoreCase(authMode);
    }

    private boolean supportsLegacy() {
        return !RedisConstants.AUTH_MODE_V2.equalsIgnoreCase(authMode);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserHolder.removeUser();
    }
}

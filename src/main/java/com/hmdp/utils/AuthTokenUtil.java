package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;

public class AuthTokenUtil {

    private static final String BEARER_PREFIX = "bearer ";

    private AuthTokenUtil() {
    }

    public static String extractToken(String authorizationHeader) {
        if (StrUtil.isBlank(authorizationHeader)) {
            return null;
        }
        String value = authorizationHeader.trim();
        if (value.length() > BEARER_PREFIX.length()
                && value.substring(0, BEARER_PREFIX.length()).toLowerCase().equals(BEARER_PREFIX)) {
            String token = value.substring(BEARER_PREFIX.length()).trim();
            return StrUtil.isBlank(token) ? null : token;
        }
        return value;
    }
}

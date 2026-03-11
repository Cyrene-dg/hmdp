package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.AuthTokenDTO;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.AuthTokenUtil;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${auth.mode:hybrid}")
    private String authMode;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_CODE_KEY + phone,
                code,
                RedisConstants.LOGIN_CODE_TTL,
                TimeUnit.MINUTES
        );
        log.debug("发送短信验证码成功，验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginFormDTO, HttpSession session) {
        Result validation = validateLogin(loginFormDTO);
        if (validation != null) {
            return validation;
        }

        User user = query().eq("phone", loginFormDTO.getPhone()).one();
        if (user == null) {
            user = createUserWithPhone(loginFormDTO.getPhone());
        }

        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = toUserDTO(user);
        Map<String, Object> userMap = toUserMap(userDTO);
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    @Override
    public Result loginV2(LoginFormDTO loginFormDTO, HttpSession session) {
        if (!supportsV2()) {
            return Result.fail("双token模式未启用");
        }

        Result validation = validateLogin(loginFormDTO);
        if (validation != null) {
            return validation;
        }

        User user = query().eq("phone", loginFormDTO.getPhone()).one();
        if (user == null) {
            user = createUserWithPhone(loginFormDTO.getPhone());
        }

        UserDTO userDTO = toUserDTO(user);
        Map<String, Object> userMap = toUserMap(userDTO);

        String accessToken = UUID.randomUUID().toString(true);
        String refreshToken = UUID.randomUUID().toString(true);

        String accessKey = RedisConstants.LOGIN_ACCESS_KEY + accessToken;
        stringRedisTemplate.opsForHash().putAll(accessKey, userMap);
        stringRedisTemplate.expire(accessKey, RedisConstants.LOGIN_ACCESS_TTL, TimeUnit.MINUTES);

        String refreshKey = RedisConstants.LOGIN_REFRESH_KEY + refreshToken;
        stringRedisTemplate.opsForHash().putAll(refreshKey, userMap);
        stringRedisTemplate.expire(refreshKey, RedisConstants.LOGIN_REFRESH_TTL, TimeUnit.MINUTES);

        return Result.ok(new AuthTokenDTO(
                "Bearer",
                accessToken,
                RedisConstants.LOGIN_ACCESS_TTL,
                refreshToken,
                RedisConstants.LOGIN_REFRESH_TTL
        ));
    }

    @Override
    public Result refreshToken(String refreshToken) {
        if (!supportsV2()) {
            return Result.fail("双token模式未启用");
        }

        String refreshTokenValue = AuthTokenUtil.extractToken(refreshToken);
        if (StrUtil.isBlank(refreshTokenValue)) {
            return Result.fail("refreshToken不能为空");
        }

        String refreshKey = RedisConstants.LOGIN_REFRESH_KEY + refreshTokenValue;
        Map<Object, Object> refreshUserMap = stringRedisTemplate.opsForHash().entries(refreshKey);
        if (refreshUserMap == null || refreshUserMap.isEmpty()) {
            return Result.fail("refreshToken无效或已过期");
        }

        Map<String, Object> userMap = new HashMap<>();
        for (Map.Entry<Object, Object> entry : refreshUserMap.entrySet()) {
            userMap.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }

        String accessToken = UUID.randomUUID().toString(true);
        String accessKey = RedisConstants.LOGIN_ACCESS_KEY + accessToken;
        stringRedisTemplate.opsForHash().putAll(accessKey, userMap);
        stringRedisTemplate.expire(accessKey, RedisConstants.LOGIN_ACCESS_TTL, TimeUnit.MINUTES);

        stringRedisTemplate.expire(refreshKey, RedisConstants.LOGIN_REFRESH_TTL, TimeUnit.MINUTES);

        return Result.ok(new AuthTokenDTO(
                "Bearer",
                accessToken,
                RedisConstants.LOGIN_ACCESS_TTL,
                refreshTokenValue,
                RedisConstants.LOGIN_REFRESH_TTL
        ));
    }

    @Override
    public Result logout(String authorizationHeader, String refreshTokenHeader) {
        String token = AuthTokenUtil.extractToken(authorizationHeader);
        String refreshToken = AuthTokenUtil.extractToken(refreshTokenHeader);

        Set<String> keys = new LinkedHashSet<>();
        if (StrUtil.isNotBlank(token)) {
            keys.add(RedisConstants.LOGIN_USER_KEY + token);
            keys.add(RedisConstants.LOGIN_ACCESS_KEY + token);
            if (StrUtil.isBlank(refreshToken)) {
                keys.add(RedisConstants.LOGIN_REFRESH_KEY + token);
            }
        }
        if (StrUtil.isNotBlank(refreshToken)) {
            keys.add(RedisConstants.LOGIN_REFRESH_KEY + refreshToken);
        }

        if (!keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
        UserHolder.removeUser();
        return Result.ok();
    }

    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;
        int dayOfMonth = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;
        int dayOfMonth = now.getDayOfMonth();
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        int count = 0;
        while (true) {
            if ((num & 1) == 0) {
                break;
            }
            count++;
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private Result validateLogin(LoginFormDTO loginFormDTO) {
        if (loginFormDTO == null || RegexUtils.isPhoneInvalid(loginFormDTO.getPhone())) {
            return Result.fail("手机号格式错误");
        }
        String code = loginFormDTO.getCode();
        String cacheCode = stringRedisTemplate.opsForValue().get(
                RedisConstants.LOGIN_CODE_KEY + loginFormDTO.getPhone()
        );
        if (!StrUtil.equals(code, cacheCode)) {
            return Result.fail("验证码错误");
        }
        return null;
    }

    private UserDTO toUserDTO(User user) {
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        return userDTO;
    }

    private Map<String, Object> toUserMap(UserDTO userDTO) {
        return BeanUtil.beanToMap(
                userDTO,
                new HashMap<String, Object>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())
        );
    }

    private boolean supportsV2() {
        return !RedisConstants.AUTH_MODE_LEGACY.equalsIgnoreCase(authMode);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_" + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}

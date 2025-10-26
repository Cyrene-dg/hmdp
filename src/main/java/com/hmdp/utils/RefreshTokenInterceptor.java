package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
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
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("刷新拦截器启动拦截");
        //实现将当前用户放到threadlocal里
        //获取当前token
        String token = request.getHeader("Authorization");
        log.info("从Authorization头获取到的token：{}", token);
        //从redis里获取当前用户
        String key = RedisConstants.LOGIN_USER_KEY + token;
        log.info("生成的Redis查询key：{}", key); // 新增：打印Redis的key
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        log.info("从Redis查询到的userMap是否为空：{}", userMap.isEmpty()); // 新增：判断userMap是否有数据
        //不存在用户
        if (userMap.isEmpty()) {
           return true;
        }
        //将用户从hash转换成bean以后才能存threadlocal
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //刷新时间
        stringRedisTemplate.expire(key,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        UserHolder.saveUser(userDTO);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}

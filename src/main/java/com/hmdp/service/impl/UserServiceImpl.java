package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

//对发送短信验证码和登录的方法进行redis改造
//    保存验证码到redis：key为手机号，value为验证码，都是String类型
//    保存用户到redis：key为随机生成的Token，String，value为hash类型储存的用户
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送短信验证码
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //检验手机号对不对
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("请输入正确的手机号码");
        }
        //生成随机数
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY+phone,code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送短信，模拟发送
        log.debug("发送验证码成功，验证码是：{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginFormDTO, HttpSession session) {
        //校验手机号
        if(RegexUtils.isPhoneInvalid(loginFormDTO.getPhone())){
            return Result.fail("请输入正确的手机号码");
        }
        //校验验证码
        //校验错的而不是对的，防止if嵌套
        String code = loginFormDTO.getCode();
        String cachePhone = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginFormDTO.getPhone());
        if(!code.equals(cachePhone)){
            return Result.fail("验证码错误");
        }
        //根据手机号查询用户
        //用到mybatis plus
        String phone = loginFormDTO.getPhone();

        User user = query().eq("phone", phone).one();
        //不存在
        if(user==null){
            //调用创建新用户的方法的逻辑
            user = creatUserWithPhone(phone);
        }
        //存在
        //保存到redis中
        //键，生成token
        String token = UUID.randomUUID().toString(true);
        //值，将user对象转换成map
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);

        //这里用到了bean转map工具
//        Map<String, Object> hashMap = BeanUtil.beanToMap(userDTO);
        //要求不能够有Long类型
        Map<String, Object> userMap = BeanUtil.beanToMap(
                userDTO,
                new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())
        );


        //将键和值储存到redis里
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);

        //设置有效期
        //存在问题，这个到期时间设计的是三十分钟，需要再拦截器里刷新时间，防止用户处于登录操作状态的时候丢失储存数据报
        stringRedisTemplate.expire(tokenKey,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    private User creatUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_"+RandomUtil.randomString(10));
        save(user);
        return user;
    }


}

package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpSession;

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

    /**
     * 发送短信验证码
     * @param phone
     * @param session
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //检验手机号对不对
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("请输入正确的手机号码");
        }
        //生成随机数
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到session
        session.setAttribute("code",code);
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
        Object cachePhone = session.getAttribute("code");
        if(cachePhone == null||!code.equals(cachePhone.toString())){
            return Result.fail("验证码错误");
        }
        //根据手机号查询用户
        //用到mybatisplus
        String phone = loginFormDTO.getPhone();

        User user = query().eq("phone", phone).one();
        //不存在
        if(user==null){
            //调用创建新用户的方法的逻辑
            user = creatUserWithPhone(phone);
        }
        //存在
        //保存到session中
        session.setAttribute("user",user);
        return Result.ok();
    }

    private User creatUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_"+RandomUtil.randomString(10));
        save(user);
        return user;
    }


}

package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private UserServiceImpl userServiceImpl;

    @Override
    public Result getBlogById(Long id) {
        //从数据库里拿blog，已知blogid，需要拿到其他blog信息以及用户icon和用户name
        //这里的用户icon和用户name的设计很巧妙
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("blog不存在");
        }
        Long userId = blog.getUserId();
        User user = userServiceImpl.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
        return Result.ok(blog);
    }
}

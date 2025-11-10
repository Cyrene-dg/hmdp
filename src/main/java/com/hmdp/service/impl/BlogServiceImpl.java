package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

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
    private IUserService userService;
    @Qualifier("stringRedisTemplate")
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * blog单体查询
     * @param id
     * @return
     */
    @Override
    public Result getBlogById(Long id) {
        //从数据库里拿blog，已知blogid，需要拿到其他blog信息以及用户icon和用户name
        //这里的用户icon和用户name的设计很巧妙
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("blog不存在");
        }
        Blog buildBlog = buildBlog(blog);
        //添加代码，检测是否已经点赞，填充isLike字段，前端接收到了以后用来判断是否高亮显示
        isLikedBlog(buildBlog);
        return Result.ok(buildBlog);
    }


    /**
     * blog分页查询
     * @param current
     * @return
     */
    @Override
    public Result getHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> { buildBlog(blog); isLikedBlog(blog); });
        return Result.ok(records);

    }

    /**
     * 点赞
     * @param id
     * @return
     */
    @Override
    public Result LikeBlog(Long id) {
        //核心思路就是利用redis的set【集合判断是否已经点赞，实现点一下点赞，再点一下取消点赞
        //获取登录用户
        Long userId = UserHolder.getUser().getId();
        //判断是否已经点赞
        //键未博客id，值为用户id
        Double isLiked = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + id,userId.toString());
        //未点赞，那就点赞数据库liked字段加一，用户存进redis的set集合里
        if(isLiked == null){
            boolean update = update().setSql("liked = liked + 1").eq("id", id).update();
            if(update){stringRedisTemplate.opsForZSet().add(RedisConstants.BLOG_LIKED_KEY + id,userId.toString(),System.currentTimeMillis());}
        }else {
            //已经点赞，那就数据库点赞数量减一，从set集合里删除
            boolean update = update().setSql("liked = liked - 1").eq("id", id).update();
            if (update){stringRedisTemplate.opsForZSet().remove(RedisConstants.BLOG_LIKED_KEY + id, userId.toString());}
        }
        return Result.ok();
    }

    /**
     * 通过bligid拿到需要的用户字段并赋值，从而构造完整的blog
     * @param blog
     * @return
     */
    private Blog buildBlog(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
        return blog;
    }


    /**
     * 查询是否点过赞并将islike字段赋值
     * @param blog
     */
    private void isLikedBlog(Blog blog) {
        Long userId = UserHolder.getUser().getId();
        Long id = blog.getId();
        Double isLiked = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + id,userId.toString());
        blog.setIsLike(isLiked != null);
    }
}

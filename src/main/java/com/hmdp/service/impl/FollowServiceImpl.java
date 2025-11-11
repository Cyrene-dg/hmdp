package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    private final StringRedisTemplate stringRedisTemplate;

    public FollowServiceImpl(@Qualifier("stringRedisTemplate") StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Autowired
    private IUserService userService;

    //关联表的数据库结构是主键，用户id：userId，关联的用户id：followUserId
    @Override
    public Result follow(Long followUserId, boolean isFollow) {
        //核心逻辑就是判断是否关注，没关注就关注，即在中间表中加入当前用户id和关注的用户id。关注了就取关，即删除关联表中相应的数据
        //都是数据库操作，不用redis
        Long userId = UserHolder.getUser().getId();

        String key = RedisConstants.FOLLOW_KEY + userId;
        //这里的前端逻辑好像是反的
        if(isFollow){
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            boolean save = save(follow);
            //存入redis
            if(save){
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }

        }else {
            boolean remove = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId)
            );
//            从redis集合里移除
            if(remove){
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }

                return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        //核心就是查数据库看是否关注，并返回true和false
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count>0);
    }

    @Override
    public Result followCommons(Long id) {
        //核心思路就是从set里获取当前用户的关注集合以及其他用户的关注集合取交集，取出有交集的用户，提取成一个user列表list返回给前端
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FOLLOW_KEY + userId;
        // 2.求交集
        String key2 = RedisConstants.FOLLOW_KEY + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect == null || intersect.isEmpty()) {
            // 无交集
            return Result.ok(Collections.emptyList());
        }
        // 3.解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        // 4.查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}

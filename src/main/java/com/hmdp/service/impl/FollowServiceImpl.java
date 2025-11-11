package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    //关联表的数据库结构是主键，用户id：userId，关联的用户id：followUserId
    @Override
    public Result follow(Long followUserId, boolean isFollow) {
        //核心逻辑就是判断是否关注，没关注就关注，即在中间表中加入当前用户id和关注的用户id。关注了就取关，即删除关联表中相应的数据
        //都是数据库操作，不用redis
        Long userId = UserHolder.getUser().getId();

        //这里的前端逻辑好像是反的
        if(isFollow){
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            save(follow);
        }else {
            remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId)
            );
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
}

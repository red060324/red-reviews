package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.swing.*;
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
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null){
            return Result.ok();
        }
        Long userId = user.getId();
        String key = "follow:" + userId;
        //判断关注或取关
        if (isFollow){
            //关注，新增数据
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            boolean isSuccess = save(follow);
            if (isSuccess){
                //把关注用户id放入redis的set集合

                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }

        }else {
            //取关，删除数据
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            if (isSuccess){
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result followOrNot(Long followUserId) {
        //获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null){
            return Result.ok();
        }
        Long userId = user.getId();
        //查询是否关注
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result commonFollow(Long id) {
        //获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null){
            return Result.ok();
        }
        Long userId = user.getId();
        String key1 = "follow:" + userId;
        String key2 = "follow:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null ||intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //解析出id
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOList = userService.listByIds(ids)
                .stream()
                .map(user1 -> BeanUtil.copyProperties(user1, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOList);
    }
}

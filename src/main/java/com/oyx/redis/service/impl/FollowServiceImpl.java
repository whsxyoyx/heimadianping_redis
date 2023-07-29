package com.oyx.redis.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.oyx.redis.bean.Follow;
import com.oyx.redis.bean.User;
import com.oyx.redis.dto.Result;
import com.oyx.redis.dto.UserDTO;
import com.oyx.redis.mapper.FollowMapper;
import com.oyx.redis.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oyx.redis.service.IUserService;
import com.oyx.redis.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
    private StringRedisTemplate redisTemplate;
    @Autowired
    private IUserService userService;

    /**
     * 笔记页面中,判断用户是否关注
     * @param id 被关注的用户id
     * @return
     */
    @Override
    public Result isFollow(Integer id) {
        //0.获取登录的用户
        UserDTO user = UserHolder.getUser();
        if(user == null){
            return Result.fail("用户还没登录!!!");
        }
        Long userId = user.getId();
        Integer count = query().eq("follow_user_id", id).eq("user_id", userId).count();
        return Result.ok(count > 0);
    }

    /**
     * 关注或者取关操作
     * @param id  被关注的用户id
     * @param isFollow  关注或者取关的判断依据
     * @return
     */
    @Override
    public Result follow(Long id, Boolean isFollow) {
        //0.获取登录的用户
        UserDTO user = UserHolder.getUser();
        if(user == null){
            return Result.fail("用户还没登录!!!");
        }
        Long userId = user.getId();
        String key = "follows:"+userId;
        //1.判断到底是关注还是者取关
        if(isFollow){
            //关注,新增数据
            Follow follow = new Follow();
            follow.setFollowUserId(id);
            follow.setUserId(userId);
            follow.setCreateTime(LocalDateTime.now());
            boolean saveSuccess = save(follow);
            //判断是否关注成功
            if(saveSuccess){
                //关注成功,将关注的用户id保存到redis中
                redisTemplate.opsForSet().add(key,id.toString());
            }
        }else{
            //取关,删除数据
            boolean removeSuccess = remove(new QueryWrapper<Follow>().eq("follow_user_id", id).eq("user_id", userId));
            if(removeSuccess){
                //取关成功,将关注的用户id从redis中移除
                redisTemplate.opsForSet().remove(key,id.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 共同关注功能
     * @param id 被关注的用户id
     * @return
     */
    @Override
    public Result commonFollow(Integer id) {
        //0.获取登录的用户
        UserDTO user = UserHolder.getUser();
        if(user == null){
            return Result.fail("用户还没登录!!!");
        }
        Long userId = user.getId();
        String key1 = "follows:"+userId;
        String key2 = "follows:"+id;
        //求交集
        Set<String> ids = redisTemplate.opsForSet().intersect(key1, key2);
        if(ids == null || ids.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //解析id
        List<Long> idsList = ids.stream().map(i -> Long.valueOf(i)).collect(Collectors.toList());
        //根据id批量查询用户
        List<UserDTO> userDTO = userService.query().in("id", idsList).list()
                .stream().map(userObj -> BeanUtil.copyProperties(userObj, UserDTO.class)).toList();
        return Result.ok(userDTO);
    }
}

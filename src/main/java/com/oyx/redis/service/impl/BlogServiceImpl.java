package com.oyx.redis.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oyx.redis.bean.Blog;
import com.oyx.redis.bean.Follow;
import com.oyx.redis.bean.User;
import com.oyx.redis.dto.Result;
import com.oyx.redis.dto.ScrollResult;
import com.oyx.redis.dto.UserDTO;
import com.oyx.redis.mapper.BlogMapper;
import com.oyx.redis.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oyx.redis.service.IFollowService;
import com.oyx.redis.service.IUserService;
import com.oyx.redis.utils.SystemConstants;
import com.oyx.redis.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.oyx.redis.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.oyx.redis.utils.RedisConstants.FEED_KEY;


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

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 查询发布笔记的用户信息
     */

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryBlogById(Integer blogId) {
        Blog blog = getById(blogId);
        if(blog == null){
            return Result.fail("笔记不存在!!!");
        }
        //查blog有关的用户相关信息
        queryBlogUser(blog);
        //查blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    //判断当前用户是否点赞
    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        //用户未登录无需查是否点赞
        if(user == null){
            return;
        }
        //1、获取登录用户消息
        Long userId = user.getId();
        String key = BLOG_LIKED_KEY + blog.getId();
        //2、判断当前用户是否已经点赞
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    //点赞
    @Override
    public Result likeBlog(Long id) {
        //1、获取登录用户消息
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        //2、判断是否点赞
        if(score == null){
            //3、未点赞
            //3.1、数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //3.2、保存用户信息到redis的set集合，作为是否点赞判断的依据
            if(isSuccess){
                redisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }else{
            //4、已点赞
            //4.1、数据库点赞-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            //4.2、把用户从redis的set的集合中移除
            if(isSuccess){
                redisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = redisTemplate.opsForZSet().range(key, 0, 4);
        List<Long> ids = new ArrayList<>();
        if(top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        top5.forEach(str ->
                ids.add(Long.valueOf(str))
        );
        String idStr = StrUtil.join(",", ids);

        List<UserDTO> userDTOS = userService.query().in("id",ids)
                .last("order by Field(id,"+idStr+")").list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    /**
     * 发布笔记
     * @param blog 前端用户写好的数据
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 2.保存探店博文
        boolean saveSuccess = save(blog);
        if(!saveSuccess){
            //3.保存不成功
            return Result.fail("笔记新增失败");
        }
        //4.保存成功,给粉丝推笔记
        //4.1查询当前用户的所有粉丝
        List<Follow> followList = followService.query().eq("follow_user_id", user.getId()).list();

        followList.stream().map(f -> f.getUserId()).toList().forEach(uid -> {
            //4.2给粉丝推送笔记
            redisTemplate.opsForZSet().add(FEED_KEY + uid, blog.getId().toString(),System.currentTimeMillis());
        });
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //0.获取登录的用户
        UserDTO user = UserHolder.getUser();
        if(user == null){
            return Result.fail("用户还没登录!!!");
        }
        Long userId = user.getId();
        String key = FEED_KEY + userId;
        //1.查收件箱--------从redis中获取关注的用户发布的笔记
        Set<ZSetOperations.TypedTuple<String>> res = redisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        //2.判断是否查询到关注用户发布的笔记
        if(res == null || res.isEmpty()){
            //2.1如果没有查询到关注的用户发布的笔记
            return Result.ok(Collections.emptyList());
        }
        //3.查询到了笔记
        //3.1 获取最小的时间戳
        long minTime = 0;
        int os = 1;
        for ( ZSetOperations.TypedTuple<String> tuple: res) {
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        //3.2 获取所有的博客id
        List<Long> blogIds = res.stream().map(typedTuple -> Long.valueOf(typedTuple.getValue())
        ).toList();

        //3.3 根据blogIds批量查询
        String idStr = StrUtil.join(",", blogIds);
        List<Blog> blogs = query().in("id", blogIds).last("order by FIELD(id,"+idStr+")").list();
        blogs.forEach(blog ->{
            //3.4查发布该笔记的用户信息
            queryBlogUser(blog);
            //3.4判断该笔记是否被点赞
            isBlogLiked(blog);
        });

        // 4. 封装并返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os); // 将本次查询跳过次数进行封装返回，避免下一次结果的重复查询
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }
}

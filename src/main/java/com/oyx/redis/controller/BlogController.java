package com.oyx.redis.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oyx.redis.dto.Result;
import com.oyx.redis.dto.UserDTO;
import com.oyx.redis.bean.Blog;
import com.oyx.redis.bean.User;
import com.oyx.redis.service.IBlogService;
import com.oyx.redis.service.IUserService;
import com.oyx.redis.utils.SystemConstants;
import com.oyx.redis.utils.UserHolder;
import com.oyx.redis.dto.Result;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;

    /**
     * 发布笔记
     * @param blog
     * @return
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /**
     * 查询最先点赞的前5个用户信息
     * @param id
     * @return
     */
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        return blogService.queryBlogLikes(id);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
       return blogService.queryHotBlog(current);
    }

    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Integer blogId){
        return blogService.queryBlogById(blogId);
    }

    /**
     *  滚动分页查询的方式实现---获取关注用户的笔记
     * @param max 上次查询的最小时间
     * @param offset 数据查询的偏移量
     * @return
     */
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(@RequestParam(value = "lastId") Long max,
                                @RequestParam(value = "offset", required = false,defaultValue = "0") Integer offset){
        return blogService.queryBlogOfFollow(max,offset);
    }
}

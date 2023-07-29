package com.oyx.redis.controller;


import com.oyx.redis.dto.Result;
import com.oyx.redis.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
//关注和取关
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Autowired
    private IFollowService followService;

    /**
     * 关注或者取关操作
     * @param id  被关注的用户id
     * @param isFollow  关注或者取关的判断依据
     * @return
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id, @PathVariable("isFollow") Boolean isFollow){
        return followService.follow(id,isFollow);
    }

    /**
     * 笔记页面中,判断用户是否关注
     * @param id 被关注的用户id
     * @return
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Integer id){
        return followService.isFollow(id);

    }
    /**
     * 共同关注功能
     * @param id 被关注的用户id
     * @return
     */
    @GetMapping("/common/{id}")
    public Result commonFollow(@PathVariable("id") Integer id){
        return followService.commonFollow(id);

    }



}

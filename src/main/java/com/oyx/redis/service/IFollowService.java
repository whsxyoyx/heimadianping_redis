package com.oyx.redis.service;

import com.oyx.redis.bean.Follow;
import com.baomidou.mybatisplus.extension.service.IService;
import com.oyx.redis.dto.Result;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    Result isFollow(Integer id);

    Result follow(Long id, Boolean isFollow);

    Result commonFollow(Integer id);
}

package com.oyx.redis.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oyx.redis.bean.User;
import com.oyx.redis.dto.LoginFormDTO;
import com.oyx.redis.dto.Result;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);
}

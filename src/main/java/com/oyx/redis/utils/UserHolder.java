package com.oyx.redis.utils;

import com.oyx.redis.dto.UserDTO;

/**
 * 用于将用户登录的信息存入ThreadLocal
 */
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}

package com.oyx.redis.interceptor;

import com.oyx.redis.dto.UserDTO;
import com.oyx.redis.utils.UserHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author oyx
 * @create 2023-07-24 10:17
 */
public class LoginInterceptor2 implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1、判断ThreadLocal有没有user用户
        UserDTO user = UserHolder.getUser();
        if(user == null){
            response.setStatus(401);
            //没有用户则拦截
            return false;
        }
        //有用户则放行
        return true;
    }

}

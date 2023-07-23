package com.oyx.redis.interceptor;

import com.oyx.redis.bean.User;
import com.oyx.redis.dto.UserDTO;
import com.oyx.redis.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author oyx
 * @create 2023-07-23 11:43
 */
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1、获取session中的userInfo
        Object userInfo = request.getSession().getAttribute("user");
        //2、判断用户是否存在
        if(userInfo == null){
            //3、不存在，返回401状态码
            response.setStatus(401);
            return false;
        }
        //4、存在，用于将用户登录的信息存入ThreadLocal
        UserHolder.saveUser((UserDTO) userInfo);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}

package com.oyx.redis.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.oyx.redis.bean.User;
import com.oyx.redis.dto.UserDTO;
import com.oyx.redis.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.oyx.redis.utils.RedisConstants.LOGIN_USER_KEY;

/**
 * @author oyx
 * @create 2023-07-23 11:43
 */
public class LoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate redisTemplate;

    public LoginInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1、获取请求头中的token
        //Object userInfo = request.getSession().getAttribute("user");
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
            //response.setStatus(401);
            return true;
        }
        //2、基于token获取redis的user用户
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);

        //3、判断用户是否存在
        if(entries.isEmpty()){
            //4、不存在，返回401状态码
            //response.setStatus(401);
            return true;
        }
        //5、将查询到的hash转为UserDTO
        UserDTO userDTO = BeanUtil.fillBeanWithMap(entries, new UserDTO(), false);
        //6、存在，用于将用户登录的信息存入ThreadLocal
        UserHolder.saveUser(userDTO);
        //7、刷新token有效期
        redisTemplate.expire(LOGIN_USER_KEY + token,30, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}

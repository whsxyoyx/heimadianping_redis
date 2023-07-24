package com.oyx.redis.config;

import com.oyx.redis.interceptor.LoginInterceptor;
import com.oyx.redis.interceptor.LoginInterceptor2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author oyx
 * @create 2023-07-23 12:20
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor(redisTemplate)).addPathPatterns("/**");
        registry.addInterceptor(new LoginInterceptor2())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/code",
                        "/user/me",
                        "/user/login",
                        "/blog/hot",
                        "/upload/**",
                        "/shop/**",
                        "/shop-type/**",
                        "/voucher/**"
                );
    }
}

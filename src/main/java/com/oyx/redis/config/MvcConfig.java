package com.oyx.redis.config;

import com.oyx.redis.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author oyx
 * @create 2023-07-23 12:20
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns("/*")
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

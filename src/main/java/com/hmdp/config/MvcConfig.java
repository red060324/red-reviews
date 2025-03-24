package com.hmdp.config;

import com.hmdp.utils.RefreshTokenInterceptor;
import com.hmdp.utils.loginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //刷新token拦截器
        registry.addInterceptor(new RefreshTokenInterceptor(redisTemplate)).addPathPatterns("/**").order(0);

        //登录拦截器
        registry.addInterceptor(new loginInterceptor())
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/blog/hot",
                        "/shop/**",
                        "/shop-type/**",
                        "/voucher/**"
                ).order(1);


    }


}

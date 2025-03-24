package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private RedisTemplate redisTemplate;

    public RefreshTokenInterceptor(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

//        //获取session
//        HttpSession session = request.getSession();
//        //获取session中的用户
//        Object user =session.getAttribute("user");

        //获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)){

            return true;
        }
        //基于token获取redis中保存的用户信息
        String tokenKey = LOGIN_USER_KEY + token;
        Map userMap = redisTemplate.opsForHash().entries(tokenKey);

        //如果不存在。拦截 返回401状态码
        if (userMap.isEmpty()){

            return true;
        }
        //存在，将用户信息保存到threadLocal中，以供后续使用
        UserDTO userDTO = new UserDTO();
        BeanUtil.fillBeanWithMap(userMap,userDTO,false);
        UserHolder.saveUser(userDTO);
        //刷新token有效期
        redisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES);
        //放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}

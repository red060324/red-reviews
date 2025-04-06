package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        //如果符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
//        //保存验证码到session
//        session.setAttribute("code",code);
        //保存验证码到redis
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //发送验证码
        log.info("发送短信验证码成功:{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            //如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
//        //校验验证码  从session中获取
//        String cacheCode = (String) session.getAttribute("code");

        //校验验证码  从redis中获取
        String cacheCode = (String) redisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null){
            return Result.fail("验证码未发送或已过期");
        }
        //不一致，报错
        if(!cacheCode.equals(code)){
            return Result.fail("验证码输入错误");
        }
        //一致，根据手机号查询数据库中用户是否存在
        User user = query().eq("phone", phone).one();
        //不存在，注册用户插入到数据库中
        if (user == null){
            user = creatUserWithPhone(phone);
        }
//        //保存用户信息到session
//        UserDTO userDTO = new UserDTO();
//        BeanUtils.copyProperties(user,userDTO);
//        session.setAttribute("user",userDTO);

        //保存用户信息到redis
        //随机生成token，作为登陆令牌
        String token = UUID.randomUUID().toString(true);
        //将user对象转为json/hashMap对象保存
        UserDTO userDTO = new UserDTO();
        BeanUtil.copyProperties(user,userDTO);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));
        //存储到redis
        String tokenKey = LOGIN_USER_KEY + token;
        redisTemplate.opsForHash().putAll(tokenKey,userMap);
        //设置有效期
        redisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);
        //将得到的token返回到浏览器客户端
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        //获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null){
            return Result.fail("用户未登录");
        }
        //获取当前登录用户id
        Long userId = user.getId();
        //获取当前日期
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //写入redis
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null){
            return Result.fail("用户未登录");
        }
        //获取当前登录用户id
        Long userId = user.getId();
        //获取当前日期
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //获取本月的签到记录，返回的是一个十进制的数字
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0)
        );
        if (result == null || result.isEmpty()){
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0){
            return Result.ok(0);
        }

        int count = 0;
        //循环遍历
        while (true){
            //让这个数字与1做与运算，得到数字的最后一个bit位,//判断这个bit位是否为0
            if ((num & 1) == 0){
                //如果为0，说明今天没有签到
                break;
            } else {
                //如果为1，说明今天已经签到，计数器加1
                count ++;
            }
            //把数字右移一位，抛弃掉最后一个bit位，
            num = num >> 1;
        }


        return Result.ok(count);
    }

    private User creatUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        save(user);
        return user;
    }
}

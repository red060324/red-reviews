package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    @Autowired
    private RedisTemplate redisTemplate;

    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        redisTemplate.opsForValue().set(key,value,time,timeUnit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        redisTemplate.opsForValue().set(key,redisData);
    }

    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit timeUnit){
        String key = keyPrefix + id;
        //从redis中查询缓存
        Object shopObject =redisTemplate.opsForValue().get(key);
        //存在，直接返回
        R value;

        if (shopObject != null && !shopObject.getClass().equals(String.class)){
            value = (R) shopObject;
            return value;
        }
        //判断命中是否为空值
        if (shopObject != null &&shopObject.getClass().equals(String.class)&& ((String) shopObject).isEmpty()){
            return null;
        }

        //不存在，查询数据库
        value = dbFallback.apply(id);
        //不存在，返回错误
        if (value == null){
            //如果不存在，在Redis中返回空值
            redisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //存在，数据写入redis，返回 并设置超时时间
        this.set(key,value,time,timeUnit);
        return value;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R,ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit){
        String key = keyPrefix + id;
        //从redis中查询缓存
        Object object =redisTemplate.opsForValue().get(key);

        //不存在，直接返回
        R value;
        if (object == null){
            return null;
        }
        //命中，判断是否过期
        RedisData redisData = (RedisData) object;
        value = (R) redisData.getData();

        if (redisData.getExpireTime().isAfter(LocalDateTime.now())){
            //未过期，返回店铺信息
            return value;
        }
        //已过期，开始缓存重建

        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        if (getLock(lockKey)){
            //成功，开启独立线程，实现缓存重建
            //再次判断是否过期
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())){
                //未过期，返回店铺信息
                return value;
            }
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                //重建缓存
                try {
                    //先查数据库
                    R value1 = dbFallback.apply(id);
                    //写入Redis
                    this.setWithLogicalExpire(key,value1,time,timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    delLock(lockKey);
                }

            });

        }
        //无论成功或失败，都返回过期信息
        return value;
    }

    //获取锁
    Boolean getLock(String lockKey){
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    void delLock(String lockKey){
        redisTemplate.delete(lockKey);
    }
}

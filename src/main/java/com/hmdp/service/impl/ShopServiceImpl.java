package com.hmdp.service.impl;

import ch.qos.logback.core.util.TimeUtil;
import cn.hutool.core.util.BooleanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public Result queryById(Long id) {
        //解决缓存穿透
//        Shop shop = queryWithPassThrough(id);

        //互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);
        if (shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    Shop queryWithMutex(Long id){
        //从redis中查询缓存
        Object shopObject =redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //存在，直接返回
        Shop shop;
        if (shopObject != null && !shopObject.getClass().equals(String.class)){
            shop = (Shop) shopObject;
            return shop;
        }
        //判断命中是否为空值
        if (shopObject != null &&shopObject.getClass().equals(String.class)&& ((String) shopObject).isEmpty()){
            return null;
        }

        //不存在，查询数据库

        //获取互斥锁

        String lockKey = LOCK_SHOP_KEY + id;
        try {
            //失败，等待，并重试
            if (!getLock(lockKey)) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //获取成功，再次查询缓存是否存在
            shopObject =redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            if (shopObject != null && !shopObject.getClass().equals(String.class)){
                shop = (Shop) shopObject;
                return shop;
            }
            //判断命中是否为空值
            if (shopObject != null &&shopObject.getClass().equals(String.class)&& ((String) shopObject).isEmpty()){
                return null;
            }
            // 查询数据库，写入Redis，释放互斥锁
            shop = getById(id);
            //模拟重建耗时
            Thread.sleep(200);
            //不存在，返回错误
            if (shop == null){
                //如果不存在，在Redis中返回空值
                redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //存在，数据写入redis，返回 并设置超时时间
            redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,shop,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            delLock(lockKey);
        }

        return shop;
    }
    
    Shop queryWithPassThrough(Long id){
        //从redis中查询缓存
        Object shopObject =redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //存在，直接返回
        Shop shop;

        if (shopObject != null && !shopObject.getClass().equals(String.class)){
            shop = (Shop) shopObject;
            return shop;
        }
        //判断命中是否为空值
        if (shopObject != null &&shopObject.getClass().equals(String.class)&& ((String) shopObject).isEmpty()){
            return null;
        }

        //不存在，查询数据库
        shop = getById(id);
        //不存在，返回错误
        if (shop == null){
            //如果不存在，在Redis中返回空值
            redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //存在，数据写入redis，返回 并设置超时时间
        redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,shop,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    @Override
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null){
            return Result.fail("店铺ID为空");
        }
        //更新数据库
        updateById(shop);

        //删除缓存
        redisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
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

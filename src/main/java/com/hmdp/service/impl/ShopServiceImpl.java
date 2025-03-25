package com.hmdp.service.impl;

import ch.qos.logback.core.util.TimeUtil;
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
        //从redis中查询缓存
        Object shopObject =redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //存在，直接返回
        Shop shop;

        if (shopObject != null && !shopObject.getClass().equals(String.class)){
            shop = (Shop) shopObject;
            return Result.ok(shop);
        }
        //判断命中是否为空值
        if (shopObject != null &&shopObject.getClass().equals(String.class)&& ((String) shopObject).isEmpty()){
            return Result.fail("店铺信息不存在");
        }

        //不存在，查询数据库
        shop = getById(id);
        //不存在，返回错误
        if (shop == null){
            //如果不存在，在Redis中返回空值
            redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }
        //存在，数据写入redis，返回 并设置超时时间
        redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,shop,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
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
}

package com.hmdp.service.impl;

import ch.qos.logback.core.util.TimeUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    @Autowired
    private CacheClient cacheClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        //解决缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id, Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id, Shop.class,this::getById,20L,TimeUnit.SECONDS);

        if (shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

//    private Shop queryWithMutex(Long id){
//        //从redis中查询缓存
//        Object shopObject =redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        //存在，直接返回
//        Shop shop;
//        if (shopObject != null && !shopObject.getClass().equals(String.class)){
//            shop = (Shop) shopObject;
//            return shop;
//        }
//        //判断命中是否为空值
//        if (shopObject != null &&shopObject.getClass().equals(String.class)&& ((String) shopObject).isEmpty()){
//            return null;
//        }
//
//        //不存在，查询数据库
//
//        //获取互斥锁
//
//        String lockKey = LOCK_SHOP_KEY + id;
//        try {
//            //失败，等待，并重试
//            if (!getLock(lockKey)) {
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            //获取成功，再次查询缓存是否存在
//            shopObject =redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//            if (shopObject != null && !shopObject.getClass().equals(String.class)){
//                shop = (Shop) shopObject;
//                return shop;
//            }
//            //判断命中是否为空值
//            if (shopObject != null &&shopObject.getClass().equals(String.class)&& ((String) shopObject).isEmpty()){
//                return null;
//            }
//            // 查询数据库，写入Redis，释放互斥锁
//            shop = getById(id);
//            //模拟重建耗时
//            Thread.sleep(200);
//            //不存在，返回错误
//            if (shop == null){
//                //如果不存在，在Redis中返回空值
//                redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            //存在，数据写入redis，返回 并设置超时时间
//            redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,shop,CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            delLock(lockKey);
//        }
//
//        return shop;
//    }
    
//    private Shop queryWithPassThrough(Long id){
//        //从redis中查询缓存
//        Object shopObject =redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        //存在，直接返回
//        Shop shop;
//
//        if (shopObject != null && !shopObject.getClass().equals(String.class)){
//            shop = (Shop) shopObject;
//            return shop;
//        }
//        //判断命中是否为空值
//        if (shopObject != null &&shopObject.getClass().equals(String.class)&& ((String) shopObject).isEmpty()){
//            return null;
//        }
//
//        //不存在，查询数据库
//        shop = getById(id);
//        //不存在，返回错误
//        if (shop == null){
//            //如果不存在，在Redis中返回空值
//            redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        //存在，数据写入redis，返回 并设置超时时间
//        redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,shop,CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        return shop;
//    }

//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

//    private Shop queryWithLogicalExpire(Long id){
//        //从redis中查询缓存
//        Object object =redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//
//        //不存在，直接返回
//        Shop shop;
//        if (object == null){
//            return null;
//        }
//        //命中，判断是否过期
//        RedisData redisData = (RedisData) object;
//        shop = (Shop) redisData.getData();
//
//        if (redisData.getExpireTime().isAfter(LocalDateTime.now())){
//            //未过期，返回店铺信息
//            return shop;
//        }
//        //已过期，开始缓存重建
//
//        //获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        if (getLock(lockKey)){
//            //成功，开启独立线程，实现缓存重建
//            //再次判断是否过期
//            if (redisData.getExpireTime().isAfter(LocalDateTime.now())){
//                //未过期，返回店铺信息
//                return shop;
//            }
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                try {
//                    this.saveShopToRedis(id,20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    //释放锁
//                    delLock(lockKey);
//                }
//
//            });
//
//        }
//        //无论成功或失败，都返回过期信息
//        return shop;
//    }

//    public void saveShopToRedis(Long id, Long expireSeconds) throws InterruptedException {
//        RedisData redisData = new RedisData();
//        Shop shop = getById(id);
//        Thread.sleep(200);
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, redisData);
//    }

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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //是否需要根据坐标查询
        if (x == null || y == null){
            //不需要，直接分页查询
            Page<Shop> page = query().eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        //计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        //查询redis、按照距离排序，分页查询
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(key, GeoReference.fromCoordinate(x, y), new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
        //解析出id
        if (results == null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from){
            return Result.ok(Collections.emptyList());
        }
        //截取从from到end的结果
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            //获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            //获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr,distance);

        });
        //根据id查询商铺信息
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        //返回结果

        return Result.ok(shops);
    }

//    //获取锁
//    Boolean getLock(String lockKey){
//        Boolean flag = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    //释放锁
//    void delLock(String lockKey){
//        redisTemplate.delete(lockKey);
//    }
}

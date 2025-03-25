package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public Result queryTypeList() {
        //查询Redis中是否存在
        List<ShopType> typeList = (List<ShopType>) redisTemplate.opsForValue().get("type:shop");
        //存在，直接返回
        if (typeList != null && !typeList.isEmpty()){
            return Result.ok(typeList);
        }
        //不存在，查询数据库
        typeList = query().orderByAsc("sort").list();
        //不存在，返回错误
        if (typeList == null || typeList.isEmpty()){
            return Result.fail("错误，商铺不存在");
        }
        //存在，写入Redis中，再返回
        redisTemplate.opsForValue().set("type:shop",typeList);
        return Result.ok(typeList);
    }
}

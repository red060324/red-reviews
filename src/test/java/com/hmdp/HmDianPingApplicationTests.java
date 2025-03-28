package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private ShopServiceImpl service;

    @Test

    void saveShop(){
        service.saveShopToRedis(1L,30L);
    }
}

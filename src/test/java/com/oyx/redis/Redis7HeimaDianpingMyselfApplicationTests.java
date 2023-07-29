package com.oyx.redis;

import com.oyx.redis.bean.Shop;
import com.oyx.redis.service.impl.ShopServiceImpl;
import com.oyx.redis.utils.CacheClient;
import com.oyx.redis.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.oyx.redis.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class Redis7HeimaDianpingMyselfApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;
    @Autowired
    private CacheClient cacheClient;
    @Autowired
    private RedisIdWorker redisIdWorker;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(500);

    @Test
    public void testRedisIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable test = () ->{
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id:"+id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            CACHE_REBUILD_EXECUTOR.submit(test);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("result："+(end - begin));
    }


    @Test
    void contextLoads() throws InterruptedException {
        //shopService.saveShopToRedis(1L,10L);
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L,shop,10L, TimeUnit.SECONDS);
    }

}

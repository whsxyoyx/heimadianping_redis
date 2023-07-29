package com.oyx.redis;

import com.oyx.redis.bean.Shop;
import com.oyx.redis.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.oyx.redis.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * @author oyx
 * @create 2023-07-28 22:49
 */
@SpringBootTest
public class GEOTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IShopService shopService;

    @Test
    public void test1(){
        List<Shop> shops = shopService.list();
        //根据typeId将店铺分组
        Map<Long, List<Shop>> map = shops.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //
        for(Map.Entry<Long, List<Shop>> entry : map.entrySet()){
            //获取类型id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            //获取同类型店铺集合
            List<Shop> shopList = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shopList.size());
            for(Shop shop :shopList){
                //写入redis
                //redisTemplate.opsForGeo().add(key, new Point(shop.getX(),shop.getY()),shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),new Point(shop.getX(),shop.getY())));
            }
            redisTemplate.opsForGeo().add(key,locations);
        }
    }
}

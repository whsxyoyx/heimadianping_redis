package com.oyx.redis.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyx.redis.bean.Shop;
import com.oyx.redis.dto.Result;
import com.oyx.redis.mapper.ShopMapper;
import com.oyx.redis.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oyx.redis.utils.CacheClient;
import com.oyx.redis.utils.RedisData;
import com.oyx.redis.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.oyx.redis.utils.RedisConstants.*;

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
    private StringRedisTemplate redisTemplate;
    @Autowired
    private CacheClient cacheClient;


    /**
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    //防止缓存穿透
    @Override
    public Result queryById(Long id) {
        //=========防止缓存穿透==========
        //未使用封装的Redis工具类的写法--实现防止缓存穿透---缓存空对象方式
        //Shop shop = queryWithPassThrough(id);
        // 使用Redis工具类实现解决缓存穿透问题---Redis工具类
        /*Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, idNum -> getById(idNum),
                CACHE_NULL_TTL, TimeUnit.MINUTES);*/

        //=========防止缓存击穿==========
        //实现防止缓存击穿---互斥锁方式
        //Shop shop = queryWithMutex(id);
        //未使用封装的Redis工具类的写法--实现防止缓存击穿---逻辑过期方式
        //Shop shop = queryWithLogicalExpire(id);
        // 使用Redis工具类实现解决缓存穿透问题---Redis工具类
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, idNum -> getById(idNum), 10L, TimeUnit.SECONDS);

        if(shop == null){
            return Result.fail("店铺不存在!!!");
        }
        return Result.ok(shop);
    }

    //----------------------逻辑过期start----------------------------

    /**
     * 解决缓存击穿----逻辑过期的方式
     */
    public void saveShopToRedis(Long id,Long expireSeconds) throws InterruptedException {
        //1、查询shop
        Shop shop = getById(id);
        Thread.sleep(200);
        //2、封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3、写入redis
        redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicalExpire(Long id){
        //1、从redis查询缓存
        String shopJSON = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2、判断reids是否存在
        if(StrUtil.isBlank(shopJSON)){
            //3、不存在，直接返回
            return null;
        }
        //4、命中，把shopJSON反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJSON, RedisData.class);
        JSONObject jsonObject = (JSONObject) redisData.getData();
        Shop shopObj = JSONUtil.toBean(jsonObject, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5、判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //6、没过期，直接返回
            return shopObj;
        }
        //7、已过期，需要缓存重建
        //7.1、尝试获取互斥锁
        String lock = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lock);
        //7.2、判断是否获取了互斥锁
        if(isLock){
            //7.3、获取了互斥锁，开启另一个线程，去获取数据库的数据
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    saveShopToRedis(id,20L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }finally {
                    //7.4、释放锁
                    unlock(lock);
                }

            });
        }
        //7.5、没获取互斥锁，返回过期数据
        return shopObj;
    }

    //----------------------逻辑过期end----------------------------

    /**
     * 互斥锁方式实现防止缓存击穿
     */
    public Shop queryWithMutex(Long id){
        //1、从redis查询缓存
        String shopJSON = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2、判断reids是否存在
        if(StrUtil.isNotBlank(shopJSON)){
            //3、存在，直接返回
            Shop shop = JSONUtil.toBean(shopJSON, Shop.class);
            return shop;
        }
        //该判断语句，用于判断shopJSON是否是""空字符串----使用缓存空对象的方式解决缓存穿透
        if(shopJSON != null){
            return null;
        }
        //4、实现缓存重建
        //4.1、获取互斥锁
        String lock = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lock);
            //4.2、判断是否成功获取
            if(!isLock){
                //4.3、失败，则休眠并重试
                    Thread.sleep(50);
                    queryWithMutex(id);
            }
            //5、成功，查询数据库
            shop = getById(id);
            //6、数据库不存在数据
            if(shop == null){
                //6.1、将空值写入redis（防止缓存穿透）----使用缓存空对象的方式解决缓存穿透
                redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                //6.2、返回错误信息
                return null;
            }
            //7、数据库存在数据,将数据写入缓存
            redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //8、释放互斥锁
            unlock(lock);
        }
        //9、返回
        return shop;
    }

    /**
     * 实现店铺信息查找----防止缓存穿透(缓存空对象的方式)
     * @param id
     * @return
     */
    //解决缓存击穿问题----原始写法
    public Shop queryWithPassThrough(Long id)
    {
        //1、从redis查询缓存
        String shopJSON = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2、判断reids是否存在
        if(StrUtil.isNotBlank(shopJSON)){
            //3、存在，直接返回
            Shop shop = JSONUtil.toBean(shopJSON, Shop.class);
            return shop;
        }

        //只有shopJSON为null或""的时候才会走下面的代码

        //该判断语句，用于判断shopJSON是否是""空字符串----使用缓存空对象的方式解决缓存穿透
        if(shopJSON != null){
            return null;
        }

        //如果不是""空字符串，就走下面的代码，去数据库里查询

        //4、不存在，查询数据库
        Shop shop = getById(id);
        //5、数据库不存在数据
        if(shop == null){
            //5.1、将空值写入redis（防止缓存穿透）----使用缓存空对象的方式解决缓存穿透
            redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"");
            redisTemplate.expire(CACHE_SHOP_KEY + id,CACHE_NULL_TTL, TimeUnit.MINUTES);
            //5.2、返回错误信息
            return null;
        }
        //6、数据库存在数据，将数据库查询到的信息放入redis缓存
        redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop));
        redisTemplate.expire(CACHE_SHOP_KEY + id,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //7、返回
        return shop;
    }

    //防止缓存击穿
    private boolean tryLock(String key){
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "oyx", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        redisTemplate.delete(key);
    }
    @Override
    @Transactional
    public Result updateShop(Shop shop) {

        Long id = shop.getId();
        if(id == null){
            return Result.fail("id不能为空");
        }
        //1、更新数据库的shop信息---添加一条商品信息
        updateById(shop);
        //2、删除redis中的shop信息的缓存
        redisTemplate.delete(CACHE_SHOP_KEY + id);

        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //1.判断是否需要根据坐标查询
        if(x == null || y == null){
            // 不需要坐标,根据数据库查询分页
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        //2.计算分页测试
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        //3.查询redis,按照距离排序,分页
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().search(key, GeoReference.fromCoordinate(x, y)
                , new Distance(5000), RedisGeoCommands.GeoSearchCommandArgs
                        .newGeoSearchArgs().includeDistance().limit(end));
        //4.解析id
        if(results == null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        if(content.size() <= from){
            //没有下一页
            return Result.ok(Collections.emptyList());
        }
        //4.1 截取from到end的部分
        List<Long> ids = new ArrayList<>(content.size());
        Map<String,Distance> distanceMap = new HashMap<>(content.size());

        content.stream().skip(from).forEach(re ->{
            //4.2 获取店铺id
            String shopIdStr = re.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            //4.3 获取距离
            Distance distance = re.getDistance();
            distanceMap.put(shopIdStr,distance);
        });
        //5.根据id查shop
        String idsStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("order by FIELD(id," + idsStr + ")").list();
        shops.stream().forEach(s ->
            s.setDistance(distanceMap.get(s.getId().toString()).getValue())
        );
        //6.返回
        return Result.ok(shops);

    }
}

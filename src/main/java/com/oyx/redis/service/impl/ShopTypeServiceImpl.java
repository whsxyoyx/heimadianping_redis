package com.oyx.redis.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.oyx.redis.bean.ShopType;
import com.oyx.redis.dto.Result;
import com.oyx.redis.mapper.ShopTypeMapper;
import com.oyx.redis.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Override
    public Result getShopTypeList() {
        //1、从redis中获取商品分类信息
        List<String> range = redisTemplate.opsForList().range(CACHE_SHOPTYPE_KEY, 0, -1);
        //2、存在直接返回
        if(range.size()>0){
            List<ShopType> shopTypes = JSONUtil.toList(range.get(0), ShopType.class);
            return Result.ok(shopTypes);
        }
        //3、不存在，则从数据库中获取
        QueryWrapper qw = new QueryWrapper();
        qw.orderByAsc("sort");
        List<ShopType> shopType = list(qw);
        //4、数据库获取不到
        if(shopType == null){
            return Result.fail("商品列表不存在");
        }
        //5、数据库获取到，则将数据缓存到redis中
        redisTemplate.opsForList().rightPush(CACHE_SHOPTYPE_KEY,JSONUtil.toJsonStr(shopType));
        //redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop));
        redisTemplate.expire(CACHE_SHOPTYPE_KEY,CACHE_SHOPTYPE_TTL, TimeUnit.MINUTES);
        return Result.ok(shopType);
    }
}

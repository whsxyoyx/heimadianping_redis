package com.oyx.redis.service;

import com.oyx.redis.bean.Shop;
import com.baomidou.mybatisplus.extension.service.IService;
import com.oyx.redis.dto.Result;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id);


    Result updateShop(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}

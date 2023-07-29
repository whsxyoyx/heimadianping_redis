package com.oyx.redis.service;

import com.oyx.redis.bean.VoucherOrder;
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
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);
}

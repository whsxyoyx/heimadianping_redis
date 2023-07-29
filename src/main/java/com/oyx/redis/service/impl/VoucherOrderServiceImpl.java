package com.oyx.redis.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.oyx.redis.bean.SeckillVoucher;
import com.oyx.redis.bean.VoucherOrder;
import com.oyx.redis.dto.Result;
import com.oyx.redis.dto.UserDTO;
import com.oyx.redis.mapper.VoucherOrderMapper;
import com.oyx.redis.service.ISeckillVoucherService;
import com.oyx.redis.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oyx.redis.utils.RedisIdWorker;
import com.oyx.redis.utils.SimpleRedisLock;
import com.oyx.redis.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private RedissonClient redissonClient;


    /**
     * 创建线程池
     */
    private static final ExecutorService ORDER_EXECUTOR = Executors.newFixedThreadPool(10);

    private IVoucherOrderService proxy;
    @PostConstruct
    public void init(){
        ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    public class VoucherOrderHandler implements Runnable{
        String queueName = "stream.orders";
        @Override
        public void run() {
            while (true){
                try {
                    //从消息队列中获取订单信息XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS streams.orders >
                    List<MapRecord<String, Object, Object>> list = redisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    if(list == null || list.isEmpty()){
                        //没有消息，继续下次循环
                        continue;
                    }
                    //有消息，解析
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //创建订单
                    handleVoucherOrder(voucherOrder);
                    //ACK确认
                    redisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log.error("创建订单异常!!!",e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while(true){
                try {
                    List<MapRecord<String, Object, Object>> list = redisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    if(list == null || list.isEmpty()){
                        //获取失败，说明pending-list没有异常消息
                        break;
                    }
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //创建订单
                    handleVoucherOrder(voucherOrder);
                    //ACK确认
                    redisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log.error("pending-list异常!!!");
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
    }

    /*//阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    public class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while (true){
                try {
                    //从队列中获取订单信息
                    VoucherOrder take = orderTasks.take();
                    //创建订单
                    handleVoucherOrder(take);
                } catch (Exception e) {
                    log.error("创建订单异常!!!");
                    e.printStackTrace();
                }
            }
        }
    }*/

    private void handleVoucherOrder(VoucherOrder voucherOrder) {

        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();

        if(!isLock){
            //获取锁失败
            log.error("不允许重复下单");
            return;
        }
        //获取锁成功
        try {
             proxy.createVoucherOrder(voucherOrder);
        }finally {
            lock.unlock();
        }
    }

    private static  final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static{
        SECKILL_SCRIPT = new DefaultRedisScript();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);

    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        long orderId = redisIdWorker.nextId("order");
        Long userId = UserHolder.getUser().getId();
        //1、执行lua脚本
        Long aLong = redisTemplate.execute(SECKILL_SCRIPT, Collections.EMPTY_LIST, voucherId.toString()
                , userId.toString(), String.valueOf(orderId));
        //2、判断结果为0
        int r = aLong.intValue();
        if(r != 0){
            //3、不为0，返回异常信息
            return  Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //4、为0，有资格购买，把下单信息保存到阻塞队列


        //获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //6、返回订单id
        return Result.ok(orderId);
    }


    /*阻塞队列的方式实现秒杀优化*/
   /* @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //1、执行lua脚本
        Long aLong = redisTemplate.execute(SECKILL_SCRIPT, Collections.EMPTY_LIST, voucherId.toString(), userId.toString());
        //2、判断结果为0
        int r = aLong.intValue();
        if(r != 0){
            //3、不为0，返回异常信息
            return  Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //4、为0，有资格购买，把下单信息保存到阻塞队列
        long orderId = redisIdWorker.nextId("order");
        //TODO 保存到阻塞队列
        //5、创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //5.1、订单编号
        voucherOrder.setId(orderId);
        //5.2、用户id
        voucherOrder.setUserId(userId);
        //5.3、代金券id
        voucherOrder.setVoucherId(voucherId);
        //5.4、放入阻塞队列，
        orderTasks.add(voucherOrder);
        //获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //6、返回订单id
        return Result.ok(orderId);
    }
*/


    @Override
    @Transactional
    public  void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        //4、一人一单限制
        //4.1、查询订单
        int count = query().eq("user_id", userId)
                .eq("voucher_id", voucherOrder.getVoucherId())
                .count();
        if(count > 0){
            //5.2、查询结果大于0,说明用户已经至少领取过一次优惠劵了
            log.error("用户已经购买过一次优惠券了");
            return;
        }

        //5、库存充足,扣减库存
        //5.1 、查询数据库中的库存数量-----CAS方案防止超卖问题
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock",0)//---判断库存大于0
                .update();
        if(!success){
            //5.2 、扣减失败
            log.error("库存不足");
            return;
        }
        //创建订单
        save(voucherOrder);
    }



/*    @Override
    public Result seckillVoucher(Long voucherId) {
        //1、查询秒杀优惠劵信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //2、判断秒杀是否开始或结束
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        LocalDateTime endTime = seckillVoucher.getEndTime();
        if(beginTime.isAfter(LocalDateTime.now())){
            //2.1、秒杀没开始，返回异常信息
            return Result.fail("秒杀没开始");
        }
        if(endTime.isBefore(LocalDateTime.now())){
            //2.2、秒杀已经结束，返回异常信息
            return Result.fail("秒杀已经结束");
        }
        //3、秒杀开始,判断库存是否充足
        Integer stock = seckillVoucher.getStock();
        if(stock < 1){
            //3.1、库存不足，返回异常信息
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        //下面代码在多个JVM的分布式系统下，锁不是唯一的，会出现问题
//        synchronized (userId.toString().intern()) {
//            //获取代理对象（事务）
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }
        //使用如下代码解决
        //获取锁对象
        //SimpleRedisLock simpleRedisLock = new SimpleRedisLock("voucher:" + userId, redisTemplate);
        //boolean isLock = simpleRedisLock.tryLock(12000);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();

        if(!isLock){
            //获取锁失败
            return Result.fail("不允许重复下单");
        }
        //获取锁成功
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }*/



   /* @Transactional
   /* @Transactional
    public  void createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //4、一人一单限制
            //4.1、查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if(count > 0){
                //5.2、查询结果大于0,说明用户已经至少领取过一次优惠劵了
                return Result.fail("用户已经购买过一次优惠券了");
            }

            //5、库存充足,扣减库存
            //5.1 、查询数据库中的库存数量-----CAS方案防止超卖问题
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock -1")
                    .eq("voucher_id", voucherId)
//                    尽管多个线程同时操作，当还剩一个库存的时候，一个线程进行了扣减库存的操作，
//                    库存变为了0，其他线程执行这个SQL修改语句，该语句会判断库存大于0才可以修改，因此可以实现
//                    防止超卖问题
                    .gt("stock",0)//---判断库存大于0
                    .update();
            if(!success){
                //5.2 、扣减失败
                return Result.fail("库存不足");
            }

            //6、创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            //6.1、订单编号
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            //6.2、用户id
            voucherOrder.setUserId(userId);
            //6.3、代金券id
            voucherOrder.setVoucherId(voucherId);
            //6.4、生成订单
            save(voucherOrder);
            //7、返回订单id
            return Result.ok(orderId);
        }*/

}

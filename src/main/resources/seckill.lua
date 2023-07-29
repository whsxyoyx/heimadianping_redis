--1.参数列表
--1.1 优惠券id
local voucherId = ARGV[1]
--1.2 用户id
local userId = ARGV[2]
--1.3 订单id
local orderId = ARGV[2]
--2.key列表
--2.1 库存key
local stockKey = 'seckill:stock:'..voucherId
--2.1 订单key
local orderKey = 'seckill:order:'..userId

--执行业务操作
if(tonumber( redis.call("get",stockKey)) < 1)then
    --库存不足
    return 1
end
if(redis.call("SISMEMBER",orderKey,userId) == 1)then
    --用户已经下单
    return 2
end
--扣库存
redis.call("incrby",stockKey,-1)
--下单，（保存用户）
redis.call("sadd",orderKey,userId)
--发消息到队列中
redis.call("XADD","stream.orders","*","userId",userId,"voucherId",voucherId,"id",orderId)
--成功
return 0

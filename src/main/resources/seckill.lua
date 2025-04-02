--优惠券id
local voucherId = ARGV[1]
--用户id
local userId = ARGV[2]
--订单id
local orderId = ARGV[3]
--库存key
local stockKey = 'seckill:stock:' .. voucherId
--订单key
local orderKey = 'seckill:order:' .. voucherId

--redis.call('set','temp',stockKey)
--脚本业务
--判断库存是否充足
local stock = tonumber(redis.call("get", stockKey)) or 0  -- 避免 nil
if stock <= 0 then
    return 1
end

--判断用户是否下单 sismember orderKey userId
if (redis.call('sismember',orderKey,userId) == 1) then
    return 2
end

--扣减库存
redis.call('incrby',stockKey,-1)
--下单（保存用户 sadd orderKey userId
redis.call('sadd',orderKey,userId)
--发送消息到队列中
redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)
return 0
local stockKey = KEYS[1]

-- Lua 脚本在 Redis 中单线程原子执行。
-- 这里故意只检查库存，不检查同一学生是否重复预约，因为业务允许同一学生重复选课。
local stock = redis.call('get', stockKey)
if stock == false or tonumber(stock) <= 0 then
    return 1
end

-- Lua 只负责原子扣减库存，预约事件随后由应用发送到 RabbitMQ。
redis.call('decr', stockKey)
return 0

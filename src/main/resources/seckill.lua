---
--- Seckill core checks (atomic in Redis Lua):
--- 1) stock > 0
--- 2) one user can only buy once
--- 3) decrement stock and mark user ordered
---

local voucherId = ARGV[1]
local userId = ARGV[2]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

local stock = tonumber(redis.call('get', stockKey))
if ((not stock) or (stock <= 0)) then
    return 1
end

if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end

redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
return 0

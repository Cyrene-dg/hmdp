--- Rollback seckill reservation atomically:
--- if user reservation exists, remove it and return one stock.

local voucherId = ARGV[1]
local userId = ARGV[2]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

if redis.call('srem', orderKey, userId) == 1 then
    redis.call('incrby', stockKey, 1)
    return 1
end

return 0

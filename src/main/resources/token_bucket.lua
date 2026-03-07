-- Token bucket for distributed rate limiting
-- 令牌桶限流脚本，用于分布式场景下做限流

-- KEYS[1]: bucket key
-- KEYS[1]：当前令牌桶在 Redis 中的 key

-- ARGV[1]: now millis
-- ARGV[1]：当前时间戳（毫秒）

-- ARGV[2]: bucket capacity
-- ARGV[2]：桶容量，即桶里最多能存多少令牌

-- ARGV[3]: refill rate (tokens per second)
-- ARGV[3]：令牌恢复速率，每秒恢复多少个令牌

-- ARGV[4]: requested tokens
-- ARGV[4]：本次请求想要消费的令牌数

local key = KEYS[1]                         -- 取出 Redis 中这个令牌桶对应的 key
local nowMillis = tonumber(ARGV[1])         -- 取出当前时间戳，并转成数字
local capacity = tonumber(ARGV[2])          -- 取出桶容量，并转成数字
local refillRate = tonumber(ARGV[3])        -- 取出令牌恢复速率，并转成数字
local requested = tonumber(ARGV[4])         -- 取出本次请求要消耗的令牌数，并转成数字

if (not nowMillis) or (not capacity) or (not refillRate) or (not requested) then  -- 如果任一参数为空或转数字失败
return 0                               -- 直接返回 0，表示不允许通过
end

if capacity <= 0 or refillRate <= 0 or requested <= 0 then  -- 如果容量、恢复速率、请求令牌数有任意一个 <= 0
return 0                               -- 参数非法，直接返回 0
end

local data = redis.call('HMGET', key, 'tokens', 'ts')  -- 从 Redis 的 Hash 中读取当前剩余令牌数 tokens 和上次更新时间 ts
local tokens = tonumber(data[1])            -- 取出 tokens，并转成数字
local lastTs = tonumber(data[2])            -- 取出 ts（上次更新时间），并转成数字

if not tokens then                          -- 如果 tokens 不存在，说明这个桶是第一次使用
tokens = capacity                       -- 初始化为满桶，即令牌数 = 桶容量
end
if not lastTs then                          -- 如果上次更新时间不存在，说明也是第一次使用
lastTs = nowMillis                      -- 将上次更新时间初始化为当前时间
end

if nowMillis > lastTs then                  -- 如果当前时间比上次更新时间晚，说明中间经过了一段时间
local delta = (nowMillis - lastTs) / 1000.0   -- 计算距离上次更新过去了多少秒
tokens = math.min(capacity, tokens + delta * refillRate)  -- 按速率补充令牌，但最多不能超过桶容量
end

local allowed = 0                           -- 默认这次请求不允许通过
if tokens >= requested then                 -- 如果当前剩余令牌数足够本次请求消费
tokens = tokens - requested             -- 扣减本次请求要消费的令牌数
allowed = 1                             -- 标记本次请求允许通过
end

redis.call('HMSET', key, 'tokens', tokens, 'ts', nowMillis)  -- 将更新后的令牌数和当前时间写回 Redis
local ttl = math.ceil((capacity / refillRate) * 2000)        -- 计算这个桶的过期时间（毫秒），大致按“从空桶回满桶时间 * 2”来设置
if ttl < 2000 then                         -- 如果算出来的 ttl 太小
ttl = 2000                             -- 至少保留 2000 毫秒，避免太快过期
end
redis.call('PEXPIRE', key, ttl)            -- 给这个桶设置毫秒级过期时间，长时间不用会自动清理

return allowed                             -- 返回结果：1 表示允许通过，0 表示不允许通过

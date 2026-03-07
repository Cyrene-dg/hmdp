-- Token bucket for distributed rate limiting
-- KEYS[1]: bucket key
-- ARGV[1]: now millis
-- ARGV[2]: bucket capacity
-- ARGV[3]: refill rate (tokens per second)
-- ARGV[4]: requested tokens

local key = KEYS[1]
local nowMillis = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local refillRate = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

if (not nowMillis) or (not capacity) or (not refillRate) or (not requested) then
    return 0
end

if capacity <= 0 or refillRate <= 0 or requested <= 0 then
    return 0
end

local data = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(data[1])
local lastTs = tonumber(data[2])

if not tokens then
    tokens = capacity
end
if not lastTs then
    lastTs = nowMillis
end

if nowMillis > lastTs then
    local delta = (nowMillis - lastTs) / 1000.0
    tokens = math.min(capacity, tokens + delta * refillRate)
end

local allowed = 0
if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
end

redis.call('HMSET', key, 'tokens', tokens, 'ts', nowMillis)
local ttl = math.ceil((capacity / refillRate) * 2000)
if ttl < 2000 then
    ttl = 2000
end
redis.call('PEXPIRE', key, ttl)

return allowed

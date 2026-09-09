local now = tonumber(ARGV[5])
local begin_at = tonumber(ARGV[6])
local end_at = tonumber(ARGV[7])

if now < begin_at or now >= end_at then
    return {6, '', '', ''}
end

local existing_digest = redis.call('hget', KEYS[2], 'digest')
if existing_digest then
    if existing_digest ~= ARGV[1] then
        return {2, '', '', ''}
    end
    if redis.call('hget', KEYS[2], 'state') == 'COMPENSATED' then
        redis.call('del', KEYS[2])
    else
        return {
            1,
            redis.call('hget', KEYS[2], 'reservationId') or '',
            redis.call('hget', KEYS[2], 'claimNo') or '',
            redis.call('hget', KEYS[2], 'eventId') or ''
        }
    end
end

local existing_reservation = redis.call('get', KEYS[3])
if existing_reservation then
    return {3, existing_reservation, '', ''}
end

local stock = tonumber(redis.call('get', KEYS[1]) or '-1')
if stock < 0 then
    return {5, '', '', ''}
end
if stock == 0 then
    return {4, '', '', ''}
end

redis.call('decr', KEYS[1])
redis.call('hset', KEYS[2],
    'digest', ARGV[1],
    'reservationId', ARGV[2],
    'claimNo', ARGV[3],
    'eventId', ARGV[4],
    'state', 'RESERVED')
redis.call('expire', KEYS[2], ARGV[8])
redis.call('set', KEYS[3], ARGV[2], 'EX', ARGV[8])
redis.call('hset', KEYS[4],
    'reservationId', ARGV[2],
    'claimNo', ARGV[3],
    'eventId', ARGV[4],
    'digest', ARGV[1],
    'campaignId', ARGV[9],
    'memberId', ARGV[10],
    'requestId', ARGV[11],
    'state', 'RESERVED',
    'reservedAt', ARGV[5])
redis.call('expire', KEYS[4], ARGV[8])
redis.call('zadd', KEYS[5], ARGV[5], ARGV[2])
redis.call('expire', KEYS[5], ARGV[8])
return {0, ARGV[2], ARGV[3], ARGV[4]}

-- KEYS[1] dedup key
-- KEYS[2] all-time zset
-- KEYS[3] daily zset
-- KEYS[4] weekly zset
-- KEYS[5] monthly zset
-- KEYS[6] hourly zset
-- KEYS[7] artist display-name hash
-- ARGV[1] artist member
-- ARGV[2] score weight
-- ARGV[3] dedup ttl
-- ARGV[4] daily ttl
-- ARGV[5] weekly ttl
-- ARGV[6] monthly ttl
-- ARGV[7] hourly ttl
-- ARGV[8] saturation scale
-- ARGV[9] display artist name

local dedup = redis.call('SET', KEYS[1], '1', 'EX', tonumber(ARGV[3]), 'NX')
if not dedup then
    return 0
end

local artist = ARGV[1]
local weight = tonumber(ARGV[2])
local saturationScale = math.max(tonumber(ARGV[8]), 1)

redis.call('HSET', KEYS[7], artist, ARGV[9])

local function incrementWithDiminishingReturn(key)
    local current = tonumber(redis.call('ZSCORE', key, artist) or '0')
    local delta = saturationScale * (
        math.log(1 + (current + weight) / saturationScale)
        - math.log(1 + current / saturationScale)
    )
    redis.call('ZINCRBY', key, delta, artist)
end

incrementWithDiminishingReturn(KEYS[2])
incrementWithDiminishingReturn(KEYS[3])
redis.call('EXPIRE', KEYS[3], tonumber(ARGV[4]))
incrementWithDiminishingReturn(KEYS[4])
redis.call('EXPIRE', KEYS[4], tonumber(ARGV[5]))
incrementWithDiminishingReturn(KEYS[5])
redis.call('EXPIRE', KEYS[5], tonumber(ARGV[6]))
incrementWithDiminishingReturn(KEYS[6])
redis.call('EXPIRE', KEYS[6], tonumber(ARGV[7]))

return 1

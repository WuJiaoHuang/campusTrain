-- KEYS[1] stock, KEYS[2] active users set, KEYS[3] initialized marker
-- ARGV[1] user id
if redis.call('EXISTS', KEYS[3]) == 0 or redis.call('EXISTS', KEYS[1]) == 0 then
    return 3
end
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return 1
end
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil then
    return 3
end
if stock <= 0 then
    return 2
end
redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
return 0

-- KEYS[1] stock, KEYS[2] active users set, KEYS[3] initialized marker
-- ARGV[1] user id
if redis.call('EXISTS', KEYS[3]) == 0 or redis.call('EXISTS', KEYS[1]) == 0 then
    return 3
end
if redis.call('SREM', KEYS[2], ARGV[1]) == 1 then
    redis.call('INCR', KEYS[1])
    return 0
end
return 1

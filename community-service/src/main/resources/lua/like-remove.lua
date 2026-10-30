if redis.call('GET', KEYS[4]) ~= '1' then return -1 end
if redis.call('SREM', KEYS[1], ARGV[1]) == 0 then return 0 end
local count = redis.call('DECR', KEYS[2])
if count < 0 then redis.call('SET', KEYS[2], 0) end
redis.call('INCR', KEYS[3])
redis.call('SADD', KEYS[5], ARGV[2])
return 1

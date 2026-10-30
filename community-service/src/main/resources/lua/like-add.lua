if redis.call('GET', KEYS[4]) ~= '1' then return -1 end
if redis.call('SADD', KEYS[1], ARGV[1]) == 0 then return 0 end
redis.call('INCR', KEYS[2])
redis.call('INCR', KEYS[3])
redis.call('SADD', KEYS[5], ARGV[2])
return 1

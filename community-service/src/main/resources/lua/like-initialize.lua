if redis.call('GET', KEYS[4]) == '1' then return 0 end
redis.call('DEL', KEYS[1])
for i = 1, #ARGV do redis.call('SADD', KEYS[1], ARGV[i]) end
redis.call('SET', KEYS[2], #ARGV)
redis.call('SET', KEYS[3], 0)
redis.call('SET', KEYS[4], '1')
return 1

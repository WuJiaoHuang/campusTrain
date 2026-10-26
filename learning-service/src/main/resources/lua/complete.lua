-- Text lesson completion is idempotent and shares the same dirty mechanism.
if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
if redis.call('HGET', KEYS[1], 'completed') == '1' then return 0 end
redis.call('HSET', KEYS[1], 'completed', '1', 'updatedAt', ARGV[2], 'traceId', ARGV[3])
redis.call('HINCRBY', KEYS[1], 'revision', 1)
redis.call('SADD', KEYS[2], ARGV[1])
return 1

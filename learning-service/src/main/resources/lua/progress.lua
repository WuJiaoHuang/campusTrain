-- KEYS: progress hash, dirty set. ARGV: member, incoming seconds, completion threshold, updatedAt, traceId.
if redis.call('EXISTS', KEYS[1]) == 0 then return {-1, 0} end
local current = tonumber(redis.call('HGET', KEYS[1], 'progressSeconds') or '0')
local incoming = tonumber(ARGV[2])
local complete = tonumber(redis.call('HGET', KEYS[1], 'completed') or '0')
local duration = tonumber(redis.call('HGET', KEYS[1], 'durationSeconds') or '0')
if incoming <= current then return {0, current} end
local first = complete == 0 and duration > 0 and incoming >= tonumber(ARGV[3])
redis.call('HSET', KEYS[1], 'progressSeconds', incoming, 'updatedAt', ARGV[4], 'traceId', ARGV[5])
if first then redis.call('HSET', KEYS[1], 'completed', 1) end
redis.call('HINCRBY', KEYS[1], 'revision', 1)
redis.call('SADD', KEYS[2], ARGV[1])
return {first and 2 or 1, incoming}

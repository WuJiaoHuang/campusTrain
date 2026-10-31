-- KEYS[1] = 点赞用户 Set：campustrain:community:like:users:{targetType}:{targetId}
-- KEYS[2] = 点赞数：campustrain:community:like:count:{targetType}:{targetId}
-- KEYS[3] = 点赞版本号：campustrain:community:like:revision:{targetType}:{targetId}
-- KEYS[4] = 初始化标记：campustrain:community:like:init:{targetType}:{targetId}
-- KEYS[5] = dirty Set：campustrain:community:like:dirty
-- ARGV[1] = 当前点赞用户 userId
-- ARGV[2] = dirty member，格式 {targetType}:{targetId}
-- return -1=尚未初始化，0=重复点赞，1=点赞成功
if redis.call('GET', KEYS[4]) ~= '1' then return -1 end
if redis.call('SADD', KEYS[1], ARGV[1]) == 0 then return 0 end
redis.call('INCR', KEYS[2])
redis.call('INCR', KEYS[3])
redis.call('SADD', KEYS[5], ARGV[2])
return 1

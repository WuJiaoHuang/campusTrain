-- KEYS[1] = 点赞用户 Set：campustrain:community:like:users:{targetType}:{targetId}
-- KEYS[2] = 点赞数：campustrain:community:like:count:{targetType}:{targetId}
-- KEYS[3] = 点赞版本号：campustrain:community:like:revision:{targetType}:{targetId}
-- KEYS[4] = 初始化标记：campustrain:community:like:init:{targetType}:{targetId}
-- ARGV[1...n] = 已点赞该目标的 userId 列表
-- return 0=已经初始化过，1=初始化成功
if redis.call('GET', KEYS[4]) == '1' then return 0 end
redis.call('DEL', KEYS[1])
for i = 1, #ARGV do redis.call('SADD', KEYS[1], ARGV[i]) end
redis.call('SET', KEYS[2], #ARGV)
redis.call('SET', KEYS[3], 0)
redis.call('SET', KEYS[4], '1')
return 1

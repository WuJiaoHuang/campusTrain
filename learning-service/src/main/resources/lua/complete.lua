-- KEYS[1] = 学习进度 Hash：campustrain:learning:progress:{userId}:{lessonId}
-- KEYS[2] = dirty Set：campustrain:learning:dirty
-- ARGV[1] = dirty member，格式 userId:lessonId
-- ARGV[2] = updatedAt，本次完成操作时间
-- ARGV[3] = traceId，链路追踪 ID
-- return -1 = 进度 key 不存在，无法完成
-- return 0 = 该课时已经完成，本次不重复处理
-- return 1 = 本次首次完成成功，并已标记 dirty
if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
if redis.call('HGET', KEYS[1], 'completed') == '1' then return 0 end
redis.call('HSET', KEYS[1], 'completed', '1', 'updatedAt', ARGV[2], 'traceId', ARGV[3])
redis.call('HINCRBY', KEYS[1], 'revision', 1)
redis.call('SADD', KEYS[2], ARGV[1])
return 1

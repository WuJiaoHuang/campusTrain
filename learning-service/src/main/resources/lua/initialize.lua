-- KEYS[1] = 学习进度 Hash：campustrain:learning:progress:{userId}:{lessonId}
-- KEYS[2] = dirty Set：campustrain:learning:dirty，记录待刷 MySQL 的 userId:lessonId
-- ARGV[1] = userId
-- ARGV[2] = lessonId
-- ARGV[3] = trainingId
-- ARGV[4] = courseId
-- ARGV[5] = chapterId
-- ARGV[6] = progressSeconds
-- ARGV[7] = durationSeconds
-- ARGV[8] = completed，0=未完成，1=已完成
-- ARGV[9] = updatedAt
-- ARGV[10] = traceId
-- ARGV[11] = isNew，1=MySQL中无记录，0=从MySQL恢复Redis
-- ARGV[12] = firstStartTime
-- return 0=key已存在，1=初始化成功
if redis.call('EXISTS', KEYS[1]) ~= 0 then return 0 end
redis.call('HSET', KEYS[1], 'userId', ARGV[1], 'lessonId', ARGV[2],
    'trainingId', ARGV[3], 'courseId', ARGV[4], 'chapterId', ARGV[5],
    'progressSeconds', ARGV[6], 'durationSeconds', ARGV[7], 'completed', ARGV[8],
    'updatedAt', ARGV[9], 'traceId', ARGV[10], 'firstStartTime', ARGV[12], 'revision', '1')
if ARGV[11] == '1' then redis.call('SADD', KEYS[2], ARGV[1] .. ':' .. ARGV[2]) end
return 1

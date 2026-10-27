-- KEYS[1] = 学习进度 Hash：campustrain:learning:progress:{userId}:{lessonId}
-- KEYS[2] = dirty Set：campustrain:learning:dirty
-- ARGV[1] = dirty member，格式 userId:lessonId
-- ARGV[2] = incoming seconds，本次上报的学习进度秒数
-- ARGV[3] = completion threshold，达到多少秒算完成
-- ARGV[4] = updatedAt，本次进度更新时间
-- ARGV[5] = traceId，链路追踪 ID
-- return {-1,0} = 进度 key 不存在，无法更新
-- return {0,current} = 本次进度 <= 当前最高进度，不更新
-- return {1,incoming} = 进度更新成功，但本次未首次完成
-- return {2,incoming} = 进度更新成功，并且本次首次达到完成条件
-- 返回值是一个两元素数组：{status, progress}
-- 第1个值 status：
-- -1 = Redis 进度 key 不存在
--  0 = 本次上报进度 <= 当前最高进度，因此没有更新
--  1 = 进度更新成功，但本次没有首次达到完成条件
--  2 = 进度更新成功，并且本次首次达到完成条件
-- 第2个值 progress：
-- 表示处理完成后系统认可的最高学习进度秒数
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

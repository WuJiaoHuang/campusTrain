-- Populate only an absent key so a concurrent request cannot overwrite newer progress.
if redis.call('EXISTS', KEYS[1]) ~= 0 then return 0 end
redis.call('HSET', KEYS[1], 'userId', ARGV[1], 'lessonId', ARGV[2],
    'trainingId', ARGV[3], 'courseId', ARGV[4], 'chapterId', ARGV[5],
    'progressSeconds', ARGV[6], 'durationSeconds', ARGV[7], 'completed', ARGV[8],
    'updatedAt', ARGV[9], 'traceId', ARGV[10], 'firstStartTime', ARGV[12], 'revision', '1')
if ARGV[11] == '1' then redis.call('SADD', KEYS[2], ARGV[1] .. ':' .. ARGV[2]) end
return 1

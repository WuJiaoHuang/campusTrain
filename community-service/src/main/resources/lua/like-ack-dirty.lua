-- KEYS[1] = 点赞版本号：campustrain:community:like:revision:{targetType}:{targetId}
-- KEYS[2] = dirty Set：campustrain:community:like:dirty
-- ARGV[1] = 本次刷盘时读取到的 revision 快照
-- ARGV[2] = dirty member，格式 {targetType}:{targetId}
-- return 1=revision 未变化且成功清除 dirty，0=revision 已变化或 dirty 不存在

if redis.call('GET', KEYS[1]) == ARGV[1] then
  return redis.call('SREM', KEYS[2], ARGV[2])
end
return 0

-- Clear only the exact version that was successfully persisted.
if redis.call('HGET', KEYS[1], 'revision') == ARGV[2] then
    return redis.call('SREM', KEYS[2], ARGV[1])
end
return 0

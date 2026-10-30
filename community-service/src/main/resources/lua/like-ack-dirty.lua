if redis.call('GET', KEYS[1]) == ARGV[1] then
  return redis.call('SREM', KEYS[2], ARGV[2])
end
return 0

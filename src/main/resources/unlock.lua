--比较线程标识与锁的标示是否一致
if (redis.call('get',KEYS[1]) == ARGV[1]) then
    return redis.call('del',KEYS[1])
end
return 0
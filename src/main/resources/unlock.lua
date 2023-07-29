--比较锁中的线程标识和当前线程的标识是否一致
if(redis.call('get',KEYS[1]) == ARGV[1])
then
    --成立，释放锁
    return redis.call('del',KEYS[1])
end
--不成立
return 0

local key = KEYS[1]
-- 多少微秒产生一个令牌， 每秒令牌数（速率）： permitsPerSecond =  1000000 / stableIntervalMicros
local stableIntervalMicros = tonumber(ARGV[1])
-- 令牌桶最大容量
local maxPermits = tonumber(ARGV[2])
-- 当前时间戳
local nowMicros = tonumber(ARGV[3])
-- 请求的许可数
local permits = tonumber(ARGV[4])

-- Hash里的KEY名称 --
local storedPermitsKey = 'storedPermits'
local nextFreeTicketMicrosKey = 'nextFreeTicketMicros'

local result = redis.call('HMGET', key, storedPermitsKey, nextFreeTicketMicrosKey)
local storedPermits = tonumber(result[1])
local nextFreeTicketMicros = tonumber(result[2])
-- 初始化
if storedPermits == nil then
  storedPermits = maxPermits
end
if nextFreeTicketMicros == nil then
  nextFreeTicketMicros = 0
end

if (nowMicros > nextFreeTicketMicros)
then
-- 更新桶里的令牌数
  local newPermits = (nowMicros - nextFreeTicketMicros) / stableIntervalMicros
  storedPermits = math.min(maxPermits, storedPermits + newPermits)
  nextFreeTicketMicros = nowMicros
else
-- 令牌生成时间戳在未来的，说明桶里已经没有令牌，也不能再借了，直接返回获取失败
  return {0, nextFreeTicketMicros, storedPermits}
end

-- 需要扣减多少桶里的令牌
local storedPermitsToSpend = math.min(permits, storedPermits)
-- 如果桶里令牌不够用，还需要的令牌数，够用，则为0
local freshPermits = permits - storedPermitsToSpend
-- 等待多长时间来生成缺少的令牌，相当于向未来借了freshPermits个令牌
local waitMicros = math.floor(freshPermits * stableIntervalMicros)
-- 不需要借，令牌生成时间戳不变，借了的话，会移到未来
nextFreeTicketMicros = nextFreeTicketMicros + waitMicros
storedPermits = storedPermits - storedPermitsToSpend

redis.call('HMSET', key, storedPermitsKey, storedPermits, nextFreeTicketMicrosKey, nextFreeTicketMicros)
-- KEY过期时间，至少60秒
local ttl = math.max(60, 2 * math.ceil((waitMicros + maxPermits * stableIntervalMicros) / 1000000))
redis.call('EXPIRE', key, ttl)
return {1, nextFreeTicketMicros, storedPermits}
-- Token Bucket — atomic check-and-decrement.
-- Returns: { allowed (1|0), remaining_tokens, retry_after_ms }
--
-- KEYS[1] = the bucket key (e.g., "rl:tb:user:u-42")
-- ARGV[1] = capacity     (e.g., 100)
-- ARGV[2] = rate_per_ms  (e.g., 0.01666 for 1/min)
-- ARGV[3] = now_ms       (server time, ms)
-- ARGV[4] = cost         (e.g., 1)
-- ARGV[5] = ttl_seconds  (e.g., 3600)

local cap   = tonumber(ARGV[1])
local rate  = tonumber(ARGV[2])
local now   = tonumber(ARGV[3])
local cost  = tonumber(ARGV[4])
local ttl   = tonumber(ARGV[5])

local data = redis.call('HMGET', KEYS[1], 't', 'ts')
local tokens = tonumber(data[1])
local last   = tonumber(data[2])
if tokens == nil then
  tokens = cap
  last = now
end

local elapsed = math.max(0, now - last)
tokens = math.min(cap, tokens + elapsed * rate)

local allowed = 0
if tokens >= cost then
  tokens = tokens - cost
  allowed = 1
end

redis.call('HSET', KEYS[1], 't', tokens, 'ts', now)
redis.call('EXPIRE', KEYS[1], ttl)

if allowed == 1 then
  return { 1, tokens, 0 }
else
  local need = cost - tokens
  local retry_ms = math.ceil(need / rate)
  return { 0, tokens, retry_ms }
end

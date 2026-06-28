-- Keys: [1] rate_limit_key
-- Args: [1] current_timestamp_ms, [2] window_start_ms, [3] max_requests
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window_start = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])

-- 1. Remove requests older than the sliding window
redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)

-- 2. Count the requests in the current window
local current_requests = redis.call('ZCARD', key)

-- 3. Check against the limit
if current_requests < limit then
    -- Add the new request
    redis.call('ZADD', key, now, now)
    -- Set TTL to clean up idle keys (e.g., window size + 1 sec)
    redis.call('PEXPIRE', key, (now - window_start) + 1000)
    return 1 -- Allowed
else
    return 0 -- Throttled
end

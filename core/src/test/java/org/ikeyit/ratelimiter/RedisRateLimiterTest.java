package org.ikeyit.ratelimiter;

import org.junit.jupiter.api.Test;

class RedisRateLimiterTest {

    @Test
    void acquire() {
        RedisRateLimiter rateLimiter = new RedisRateLimiter(null, "",  1000);
        rateLimiter.tryAcquire();
    }
}
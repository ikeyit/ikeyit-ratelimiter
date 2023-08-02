package org.ikeyit.ratelimiter;


import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * redis实现的分布式限速器，基于令牌桶算法，基本逻辑类似guava RateLimiter
 */
public class RedisRateLimiter {

    private Result result;

    private final RedisCommandExecutor redisCommandExecutor;

    private final String key;

    private final double permitsPerSecond;

    private final double maxPermits;

    /**
     * 构建
     * @param key 调用方自定义key
     * @param permitsPerSecond 每秒可以使用的令牌数, 速率例如最大qps
     */
    public RedisRateLimiter(RedisCommandExecutor redisCommandExecutor, String key, double permitsPerSecond) {
        this(redisCommandExecutor, key, permitsPerSecond, permitsPerSecond);
    }

    /**
     * 构建
     * @param key 调用方自定义key
     * @param permitsPerSecond 每秒可以使用的令牌数, 速率例如最大qps
     * @param maxPermits 令牌桶的容量
     */
    public RedisRateLimiter(RedisCommandExecutor redisCommandExecutor, String key, double permitsPerSecond, double maxPermits) {
        if (key == null) {
            throw new IllegalArgumentException("key should be not null");
        }
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond should be more than 0");
        }
        if (maxPermits <= 0) {
            throw new IllegalArgumentException("maxPermits should be more than 0");
        }
        this.redisCommandExecutor = redisCommandExecutor;
        this.key = key;
        this.permitsPerSecond = permitsPerSecond;
        this.maxPermits = maxPermits;
    }

    /**
     * 从限速器获取1个令牌
     * @return 是否成功获取令牌，如果redis限速器出错，也返回true
     */
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * 从限速器获取指定数量个令牌
     * @param permits 申请的令牌数
     * @return 是否成功获取令牌，如果redis限速器出错，也返回true
     */
    public boolean tryAcquire(long permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits should be more than 0");
        }
        long nowMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
        // 性能优化点
        // 令牌生成时间戳在未来的，说明桶里已经没有令牌，也不能再借了。
        // 简单讲没有令牌可用时，在下次令牌发放时间之间都不需要再请求redis了，减少redis请求量
        // Fail Fast
        if (result != null && nowMicros <= result.nextFreeTicketMicros) {
            return false;
        }
        synchronized (this) {
            if (result != null && nowMicros <= result.nextFreeTicketMicros) {
                return false;
            }
            result = executeScript(nowMicros, permits);
            return result == null || result.isAllowed();
        }
    }

    private Result executeScript(long nowMicros, long permits) {
        try {
            //间隔，默认1秒
            String stableIntervalMicros = Double.toString(TimeUnit.SECONDS.toMicros(1L) / permitsPerSecond);
            //执行lua脚本
            List<Object> scriptResult = redisCommandExecutor.eval(SCRIPT, key,
                            stableIntervalMicros,
                            //桶的容量
                            Double.toString(maxPermits),
                            Long.toString(nowMicros),
                            //请求的许可
                            Long.toString(permits));
            return new Result((Long) scriptResult.get(0) > 0, (Long) scriptResult.get(1), (Long) scriptResult.get(2));
        } catch (Exception e) {
            throw new RateLimiterException("fail to execute script", e);
        }
    }


    //保存lua脚本返回的结果
    static class Result {

        //是否获取令牌成功
        private boolean allowed;

        //令牌桶里的令牌数刷新时间戳，可能是未来的一个时间点
        private long nextFreeTicketMicros;

        //令牌桶里剩余的令牌数
        private long storedPermits;

        public Result(boolean allowed, long nextFreeTicketMicros, long storedPermits) {
            this.allowed = allowed;
            this.nextFreeTicketMicros = nextFreeTicketMicros;
            this.storedPermits = storedPermits;
        }

        public long getNextFreeTicketMicros() {
            return nextFreeTicketMicros;
        }

        public long getStoredPermits() {
            return storedPermits;
        }

        public boolean isAllowed() {
            return allowed;
        }
    }

    private static final String SCRIPT = loadScript();

    private static String loadScript() {
        try (InputStream is = RedisRateLimiter.class.getClassLoader().getResourceAsStream("org/ikeyit/ratelimiter/RateLimiter.lua")) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Can't load RateLimiter.lua", e);
        }
    }
}

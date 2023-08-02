package org.ikeyit.ratelimiter;

public class RateLimiterException extends RuntimeException {
    public RateLimiterException() {
    }

    public RateLimiterException(String message) {
        super(message);
    }

    public RateLimiterException(String message, Throwable cause) {
        super(message, cause);
    }

    public RateLimiterException(Throwable cause) {
        super(cause);
    }

    public RateLimiterException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}

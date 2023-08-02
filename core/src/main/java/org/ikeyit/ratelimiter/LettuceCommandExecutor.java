package org.ikeyit.ratelimiter;

import java.util.List;

public class LettuceCommandExecutor implements RedisCommandExecutor {
    @Override
    public List<Object> eval(String script, String key, String... args) {
        return null;
    }
}

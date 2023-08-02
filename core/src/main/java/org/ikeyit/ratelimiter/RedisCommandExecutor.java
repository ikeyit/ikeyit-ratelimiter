package org.ikeyit.ratelimiter;

import java.util.List;

public interface RedisCommandExecutor {
     List<Object> eval(String script, String key, String... args);
}

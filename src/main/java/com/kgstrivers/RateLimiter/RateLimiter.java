package com.kgstrivers.RateLimiter;

import javax.xml.transform.Result;
import java.time.Duration;
import java.time.Instant;

public interface RateLimiter {
    Result allow(String key, int cost);
    record Result(boolean allowed, int limit, int remaining, Instant resetAt, Duration retryAfter){}
}

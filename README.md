# Rate-Limiter


```
The selected code is the core logic of the allow method in a token bucket rate limiter. Here’s what it does:
If the cost is less than or equal to 0, it defaults to 1.
It retrieves (or creates) a Bucket for the given key, initializing it with the full burst of tokens and the current time.
It synchronizes on the bucket to ensure thread safety.
It calculates how much time has passed since the last request and refills tokens accordingly, up to the burst limit.
If enough tokens are available for the cost, it deducts the tokens and allows the request; otherwise, it denies it.
It prints the current state for debugging.
If allowed, it returns a Result indicating success, the burst, remaining tokens, and reset time.
If not allowed, it calculates how long to wait before enough tokens are available and returns a Result indicating failure, with the retry duration.
```



```
package com.kgstrivers.RateLimiter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class TokenBucketLimiter implements RateLimiter{

    private static final class Bucket{
        double token;
        long lastNs;
    }
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final double retePerSec;
    private final int burst;
    private final Clock clock;

    public TokenBucketLimiter(double ratePerSec, int burst) {
        this(ratePerSec, burst, Clock.systemUTC());
    }

    public TokenBucketLimiter(double ratePerSec, int burst, Clock clock) {
        if(ratePerSec<=0 || burst<=0){
            throw new IllegalArgumentException("Rate per second and burst must be greater than 0");
        }

        this.retePerSec = ratePerSec;
        this.burst = burst;
        this.clock = Objects.requireNonNull(clock);
    }


    @Override
    public Result allow(String key, int cost) {
        if(cost<=0) {
            cost = 1; // Default cost if not specified
        }
        final Bucket b = buckets.computeIfAbsent(key, k -> {
            Bucket nb = new Bucket();
            nb.token = burst;
            nb.lastNs = System.nanoTime();// Convert
            return nb;
        });


        boolean allowed;
        double after;
        long nowNs = System.nanoTime();
        synchronized (b){
            double delta = (nowNs - b.lastNs)/1_000_000_000.0;
            b.token = Math.min(burst, b.token + delta * retePerSec);
            b.lastNs = nowNs;


            if(b.token >= cost){
                b.token -= cost;
                allowed = true;
                after = b.token;
            } else {
                allowed = false;
            }

            after = b.token;
            System.out.printf("Key: %s, Cost: %d, Allowed: %s, After: %.2f%n", key, cost, allowed, after);
        }


        int remaining = (int) Math.floor(after);
        if(allowed){
            Instant reset = clock.instant().plusNanos((long) (1_000_000_000L / retePerSec));

            return new RateLimiter.Result(
                true,
                burst,
                Math.max(0,remaining),
                reset,
                Duration.ZERO
            );
        }else {
            double deficit = cost - after;
            double seconds = deficit / retePerSec;
            Duration retry = Duration.ofNanos((long)(seconds * 1_000_000_000L));
            Instant reset = clock.instant().plus(retry);
            return new RateLimiter.Result(false, burst, Math.max(0, remaining), reset, retry);
        }
    }
}


```


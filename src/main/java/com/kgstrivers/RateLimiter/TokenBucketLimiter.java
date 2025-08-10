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
    private final double ratePerSec;
    private final int burst;
    private final Clock clock;

    public TokenBucketLimiter(double ratePerSec, int burst) {
        this(ratePerSec, burst, Clock.systemUTC());
    }

    public TokenBucketLimiter(double ratePerSec, int burst, Clock clock) {
        if(ratePerSec<=0 || burst<=0){
            throw new IllegalArgumentException("Rate per second and burst must be greater than 0");
        }

        this.ratePerSec = ratePerSec;
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
            b.token = Math.min(burst, b.token + delta * ratePerSec);
            //The Math.min(burst, b.token + delta * ratePerSec) ensures that the token count never
            // exceeds the maximum burst capacity. Even if enough time has passed to
            // theoretically add more tokens, the bucket cannot hold more than burst tokens,
            // preventing overflow and enforcing the rate limit.
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
            Instant reset = clock.instant().plusNanos((long) (1_000_000_000L / ratePerSec));
            //The selected line calculates the next reset time for the rate limiter after a successful request.
            // It gets the current time from the provided Clock and adds the time (in nanoseconds)
            // required to generate one token at the configured rate.
            // This represents when the next token will be available in the bucket.


            /*Example: Suppose ratePerSec = 5.0 (5 tokens per second).
            1_000_000_000L / ratePerSec = 1,000,000,000 / 5 = 200,000,000 nanoseconds (0.2 seconds)
            clock.instant() returns the current time, e.g., 2024-06-10T12:00:00Z
            plusNanos(200_000_000) adds 0.2 seconds
            Result: If now is 2024-06-10T12:00:00Z,
            reset = 2024-06-10T12:00:00.200Z (next token available in 0.2 seconds)
            */
            return new RateLimiter.Result(
                true,
                burst,
                Math.max(0,remaining),
                reset,
                Duration.ZERO
            );
        }else {
            double deficit = cost - after;
            double seconds = deficit / ratePerSec;
            Duration retry = Duration.ofNanos((long)(seconds * 1_000_000_000L));

            /*
                Example: If cost = 3, after = 1.5, ratePerSec = 2.0
                deficit = 1.5
                seconds = 1.5 / 2.0 = 0.75
                retry = Duration.ofNanos((long)(0.75 * 1_000_000_000L)) = Duration.ofNanos(750_000_000) (750 ms)
             */
            Instant reset = clock.instant().plus(retry);
            return new RateLimiter.Result(false, burst, Math.max(0, remaining), reset, retry);
        }
    }
}

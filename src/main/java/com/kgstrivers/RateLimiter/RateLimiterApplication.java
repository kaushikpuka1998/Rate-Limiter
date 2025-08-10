package com.kgstrivers.RateLimiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RateLimiterApplication {

	public static void main(String[] args) {
		SpringApplication.run(RateLimiterApplication.class, args);

		RateLimiter local = new TokenBucketLimiter(10.0, 20);

		// Simulate
		for (int i = 0; i < 25; i++) {
			var r = local.allow("user:123", 1);

			System.out.printf("[%02d] allowed=%s remaining=%d retryAfter=%s%n",
					i, r.allowed(), r.remaining(), r.retryAfter());
		}

	}

}

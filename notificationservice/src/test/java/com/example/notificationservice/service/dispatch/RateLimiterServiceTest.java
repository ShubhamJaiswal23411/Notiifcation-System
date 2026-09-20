package com.example.notificationservice.service.dispatch;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class RateLimiterServiceTest {

    private final RateLimiterService rateLimiter = new RateLimiterService();

    @Test
    void allowsRequestsUnderLimit() {
        assertThat(rateLimiter.tryConsume("tenant1:EMAIL", 3)).isTrue();
        assertThat(rateLimiter.tryConsume("tenant1:EMAIL", 3)).isTrue();
        assertThat(rateLimiter.tryConsume("tenant1:EMAIL", 3)).isTrue();
    }

    @Test
    void blocksRequestOverLimit() {
        rateLimiter.tryConsume("tenant2:SMS", 2);
        rateLimiter.tryConsume("tenant2:SMS", 2);
        assertThat(rateLimiter.tryConsume("tenant2:SMS", 2)).isFalse();
    }

    @Test
    void differentKeysAreIndependent() {
        rateLimiter.tryConsume("tenantA:EMAIL", 1);
        assertThat(rateLimiter.tryConsume("tenantA:EMAIL", 1)).isFalse();
        assertThat(rateLimiter.tryConsume("tenantB:EMAIL", 1)).isTrue();
    }

    @Test
    void resetWindowClearsAllCounters() {
        rateLimiter.tryConsume("tenantC:EMAIL", 1);
        assertThat(rateLimiter.tryConsume("tenantC:EMAIL", 1)).isFalse();

        rateLimiter.resetWindow();

        assertThat(rateLimiter.tryConsume("tenantC:EMAIL", 1)).isTrue();
    }
}
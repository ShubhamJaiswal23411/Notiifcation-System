package com.example.notificationservice.service.dispatch;

import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class RetryBackoffCalculator {

    private static final int MAX_ATTEMPTS = 5;
    private static final long BASE_SECONDS = 30;
    private static final long MAX_SECONDS = 3600;

    public boolean hasAttemptsRemaining(int attemptCount) {
        return attemptCount < MAX_ATTEMPTS;
    }

    public Duration nextDelay(int attemptCount) {
        long seconds = BASE_SECONDS * (long) Math.pow(2, attemptCount - 1);
        return Duration.ofSeconds(Math.min(seconds, MAX_SECONDS));
    }
}
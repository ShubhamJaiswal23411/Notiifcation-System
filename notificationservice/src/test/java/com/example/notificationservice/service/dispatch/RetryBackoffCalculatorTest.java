package com.example.notificationservice.service.dispatch;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

public class RetryBackoffCalculatorTest {

    private final RetryBackoffCalculator calculator = new RetryBackoffCalculator();

    @Test
    void allowsRetryUnderMaxAttempts() {
        assertThat(calculator.hasAttemptsRemaining(1)).isTrue();
        assertThat(calculator.hasAttemptsRemaining(4)).isTrue();
    }

    @Test
    void blocksRetryAtMaxAttempts() {
        assertThat(calculator.hasAttemptsRemaining(5)).isFalse();
        assertThat(calculator.hasAttemptsRemaining(6)).isFalse();
    }

    @Test
    void delayDoublesWithEachAttempt() {
        assertThat(calculator.nextDelay(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(calculator.nextDelay(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(calculator.nextDelay(3)).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void delayIsCappedAtMaximum() {
        assertThat(calculator.nextDelay(10)).isEqualTo(Duration.ofSeconds(3600));
    }
}
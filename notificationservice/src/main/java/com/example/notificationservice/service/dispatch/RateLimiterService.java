package com.example.notificationservice.service.dispatch;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;

@Component
public class RateLimiterService {

    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public boolean tryConsume(String key, int limitPerMinute) {
        AtomicInteger counter = counters.computeIfAbsent(key, k -> new AtomicInteger(0));
        int current = counter.incrementAndGet();
        if (current > limitPerMinute) {
            counter.decrementAndGet();
            return false;
        }
        return true;
    }

    @Scheduled(fixedRate = 60000)
    public void resetWindow() {
        counters.clear();
    }
}
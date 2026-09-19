package com.example.notificationservice.service.dispatch;

import com.example.notificationservice.domain.enums.ChannelType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Random;

@Component
@Slf4j
public class MockNotificationProvider {

    private final Random random = new Random();

    public boolean send(ChannelType channelType, String recipient, String subject, String body) {
        log.info("Dispatching {} to {} | subject={}", channelType, recipient, subject);
        return random.nextInt(100) < 85; // ~85% simulated success rate
    }
}
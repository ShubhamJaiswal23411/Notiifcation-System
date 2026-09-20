package com.example.notificationservice.service.dispatch;

import com.example.notificationservice.domain.enums.ChannelType;

public interface NotificationProvider {
    boolean send(ChannelType channelType, String recipient, String subject, String body);
}
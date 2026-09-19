package com.example.notificationservice.dto.ratelimit;

import com.example.notificationservice.domain.enums.ChannelType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RateLimitResponse {
    private Long id;
    private ChannelType channelType;
    private int maxPerMinute;
    private int maxPerDay;
}
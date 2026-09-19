package com.example.notificationservice.dto.ratelimit;

import com.example.notificationservice.domain.enums.ChannelType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetRateLimitRequest {
    @NotNull
    private ChannelType channelType;
    @Min(1)
    private int maxPerMinute;
    @Min(1)
    private int maxPerDay;
}
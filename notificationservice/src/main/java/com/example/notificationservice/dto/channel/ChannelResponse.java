package com.example.notificationservice.dto.channel;

import com.example.notificationservice.domain.enums.ChannelType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChannelResponse {
    private Long id;
    private ChannelType channelType;
    private String configJson;
    private boolean enabled;
}
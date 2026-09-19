package com.example.notificationservice.dto.channel;

import com.example.notificationservice.domain.enums.ChannelType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateChannelRequest {

    @NotNull
    private ChannelType channelType;

    private String configJson;
}
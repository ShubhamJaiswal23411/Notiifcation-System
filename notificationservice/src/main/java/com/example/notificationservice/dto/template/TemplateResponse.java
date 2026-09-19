package com.example.notificationservice.dto.template;

import com.example.notificationservice.domain.enums.ChannelType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TemplateResponse {
    private Long id;
    private ChannelType channelType;
    private String name;
    private String subject;
    private String body;
    private int version;
}
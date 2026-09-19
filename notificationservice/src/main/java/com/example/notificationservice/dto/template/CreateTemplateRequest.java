package com.example.notificationservice.dto.template;

import com.example.notificationservice.domain.enums.ChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTemplateRequest {

    @NotNull
    private ChannelType channelType;

    @NotBlank
    private String name;

    private String subject;

    @NotBlank
    private String body;
}
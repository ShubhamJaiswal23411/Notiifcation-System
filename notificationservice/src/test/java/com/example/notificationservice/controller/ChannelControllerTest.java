package com.example.notificationservice.controller;

import com.example.notificationservice.config.SecurityConfig;
import com.example.notificationservice.domain.enums.ChannelType;
import com.example.notificationservice.dto.channel.ChannelResponse;
import com.example.notificationservice.dto.channel.CreateChannelRequest;
import com.example.notificationservice.security.AppUserDetailsService;
import com.example.notificationservice.security.JwtService;
import com.example.notificationservice.service.ChannelService;
import com.example.notificationservice.support.TestAuth;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChannelController.class)
@Import(SecurityConfig.class)
public class ChannelControllerTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean private ChannelService channelService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private AppUserDetailsService appUserDetailsService;

    @Test
    void createChannel_tenantAdmin_returns201() throws Exception {
        ChannelResponse response = new ChannelResponse(1L, ChannelType.EMAIL, "{}", true);
        when(channelService.createChannel(eq(1L), any())).thenReturn(response);

        CreateChannelRequest request = new CreateChannelRequest();
        request.setChannelType(ChannelType.EMAIL);
        request.setConfigJson("{}");

        mockMvc.perform(post("/api/channels")
                        .with(authentication(TestAuth.tenantAdmin(1L, "admin@acme.com", 1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.channelType").value("EMAIL"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void createChannel_platformAdmin_returns403() throws Exception {
        CreateChannelRequest request = new CreateChannelRequest();
        request.setChannelType(ChannelType.EMAIL);

        mockMvc.perform(post("/api/channels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createChannel_noAuth_returns401() throws Exception {
        CreateChannelRequest request = new CreateChannelRequest();
        request.setChannelType(ChannelType.EMAIL);

        mockMvc.perform(post("/api/channels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listChannels_tenantAdmin_returnsList() throws Exception {
        when(channelService.listChannels(1L)).thenReturn(
                List.of(new ChannelResponse(1L, ChannelType.SMS, "{}", true)));

        mockMvc.perform(get("/api/channels")
                        .with(authentication(TestAuth.tenantAdmin(1L, "admin@acme.com", 1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].channelType").value("SMS"));
    }
}
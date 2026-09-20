package com.example.notificationservice.controller;

import com.example.notificationservice.config.SecurityConfig;
import com.example.notificationservice.domain.enums.ChannelType;
import com.example.notificationservice.dto.ratelimit.RateLimitResponse;
import com.example.notificationservice.dto.ratelimit.SetRateLimitRequest;
import com.example.notificationservice.security.AppUserDetailsService;
import com.example.notificationservice.security.JwtService;
import com.example.notificationservice.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RateLimitController.class)
@Import(SecurityConfig.class)
public class RateLimitControllerTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean private RateLimitService rateLimitService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private AppUserDetailsService appUserDetailsService;

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void setRateLimit_platformAdmin_returns200() throws Exception {
        RateLimitResponse response = new RateLimitResponse(1L, ChannelType.EMAIL, 10, 500);
        when(rateLimitService.setRateLimit(eq(1L), any())).thenReturn(response);

        SetRateLimitRequest request = new SetRateLimitRequest();
        request.setChannelType(ChannelType.EMAIL);
        request.setMaxPerMinute(10);
        request.setMaxPerDay(500);

        mockMvc.perform(put("/api/tenants/1/rate-limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPerMinute").value(10));
    }

    @Test
    @WithMockUser(authorities = "ROLE_TENANT_ADMIN")
    void setRateLimit_tenantAdmin_returns403() throws Exception {
        SetRateLimitRequest request = new SetRateLimitRequest();
        request.setChannelType(ChannelType.EMAIL);
        request.setMaxPerMinute(10);
        request.setMaxPerDay(500);

        mockMvc.perform(put("/api/tenants/1/rate-limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void setRateLimit_noAuth_returns401() throws Exception {
        SetRateLimitRequest request = new SetRateLimitRequest();
        request.setChannelType(ChannelType.EMAIL);
        request.setMaxPerMinute(10);
        request.setMaxPerDay(500);

        mockMvc.perform(put("/api/tenants/1/rate-limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void setRateLimit_invalidMaxPerMinute_returns400() throws Exception {
        SetRateLimitRequest request = new SetRateLimitRequest();
        request.setChannelType(ChannelType.EMAIL);
        request.setMaxPerMinute(0); // violates @Min(1)
        request.setMaxPerDay(500);

        mockMvc.perform(put("/api/tenants/1/rate-limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
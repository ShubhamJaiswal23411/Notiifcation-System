package com.example.notificationservice.service;

import com.example.notificationservice.domain.RateLimit;
import com.example.notificationservice.domain.Tenant;
import com.example.notificationservice.domain.enums.ChannelType;
import com.example.notificationservice.dto.ratelimit.SetRateLimitRequest;
import com.example.notificationservice.exception.ResourceNotFoundException;
import com.example.notificationservice.repository.RateLimitRepository;
import com.example.notificationservice.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RateLimitServiceTest {

    @Mock private RateLimitRepository rateLimitRepository;
    @Mock private TenantRepository tenantRepository;
    @InjectMocks private RateLimitService rateLimitService;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(1L);
    }

    @Test
    void setRateLimit_noExistingLimit_createsNew() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(rateLimitRepository.findByTenantIdAndChannelType(1L, ChannelType.EMAIL)).thenReturn(Optional.empty());
        RateLimit saved = new RateLimit();
        saved.setId(1L);
        saved.setChannelType(ChannelType.EMAIL);
        saved.setMaxPerMinute(10);
        saved.setMaxPerDay(500);
        when(rateLimitRepository.save(any(RateLimit.class))).thenReturn(saved);

        SetRateLimitRequest request = new SetRateLimitRequest();
        request.setChannelType(ChannelType.EMAIL);
        request.setMaxPerMinute(10);
        request.setMaxPerDay(500);

        var response = rateLimitService.setRateLimit(1L, request);

        assertThat(response.getMaxPerMinute()).isEqualTo(10);
    }

    @Test
    void setRateLimit_existingLimit_updatesInPlace() {
        RateLimit existing = new RateLimit();
        existing.setId(5L);
        existing.setChannelType(ChannelType.SMS);
        existing.setMaxPerMinute(5);
        existing.setMaxPerDay(100);

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(rateLimitRepository.findByTenantIdAndChannelType(1L, ChannelType.SMS)).thenReturn(Optional.of(existing));
        when(rateLimitRepository.save(any(RateLimit.class))).thenAnswer(inv -> inv.getArgument(0));

        SetRateLimitRequest request = new SetRateLimitRequest();
        request.setChannelType(ChannelType.SMS);
        request.setMaxPerMinute(20);
        request.setMaxPerDay(1000);

        var response = rateLimitService.setRateLimit(1L, request);

        assertThat(response.getId()).isEqualTo(5L); // same row updated, not a new one
        assertThat(response.getMaxPerMinute()).isEqualTo(20);
    }

    @Test
    void setRateLimit_tenantNotFound_throws() {
        when(tenantRepository.findById(404L)).thenReturn(Optional.empty());

        SetRateLimitRequest request = new SetRateLimitRequest();
        request.setChannelType(ChannelType.EMAIL);
        request.setMaxPerMinute(1);
        request.setMaxPerDay(1);

        assertThatThrownBy(() -> rateLimitService.setRateLimit(404L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
package com.example.notificationservice.service;

import com.example.notificationservice.domain.RateLimit;
import com.example.notificationservice.domain.Tenant;
import com.example.notificationservice.dto.ratelimit.*;
import com.example.notificationservice.exception.ResourceNotFoundException;
import com.example.notificationservice.repository.RateLimitRepository;
import com.example.notificationservice.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RateLimitRepository rateLimitRepository;
    private final TenantRepository tenantRepository;

    @Transactional
    public RateLimitResponse setRateLimit(Long tenantId, SetRateLimitRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        RateLimit rateLimit = rateLimitRepository.findByTenantIdAndChannelType(tenantId, request.getChannelType())
                .orElseGet(RateLimit::new);

        rateLimit.setTenant(tenant);
        rateLimit.setChannelType(request.getChannelType());
        rateLimit.setMaxPerMinute(request.getMaxPerMinute());
        rateLimit.setMaxPerDay(request.getMaxPerDay());

        RateLimit saved = rateLimitRepository.save(rateLimit);
        return new RateLimitResponse(saved.getId(), saved.getChannelType(), saved.getMaxPerMinute(), saved.getMaxPerDay());
    }
}
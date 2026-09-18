package com.example.notificationservice.repository;

import com.example.notificationservice.domain.RateLimit;
import com.example.notificationservice.domain.enums.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RateLimitRepository extends JpaRepository<RateLimit, Long> {
    Optional<RateLimit> findByTenantIdAndChannelType(Long tenantId, ChannelType channelType);
}
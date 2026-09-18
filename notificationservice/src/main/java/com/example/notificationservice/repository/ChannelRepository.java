package com.example.notificationservice.repository;

import com.example.notificationservice.domain.Channel;
import com.example.notificationservice.domain.enums.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    List<Channel> findByTenantId(Long tenantId);
    Optional<Channel> findByTenantIdAndChannelType(Long tenantId, ChannelType channelType);
}
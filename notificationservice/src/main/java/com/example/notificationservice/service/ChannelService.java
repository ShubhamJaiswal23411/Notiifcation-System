package com.example.notificationservice.service;

import com.example.notificationservice.domain.Channel;
import com.example.notificationservice.domain.Tenant;
import com.example.notificationservice.dto.channel.*;
import com.example.notificationservice.exception.ResourceNotFoundException;
import com.example.notificationservice.repository.ChannelRepository;
import com.example.notificationservice.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final TenantRepository tenantRepository;

    @Transactional
    public ChannelResponse createChannel(Long tenantId, CreateChannelRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        Channel channel = new Channel();
        channel.setTenant(tenant);
        channel.setChannelType(request.getChannelType());
        channel.setConfigJson(request.getConfigJson());
        channel.setEnabled(true);

        Channel saved = channelRepository.save(channel);
        return toResponse(saved);
    }

    public List<ChannelResponse> listChannels(Long tenantId) {
        return channelRepository.findByTenantId(tenantId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private ChannelResponse toResponse(Channel c) {
        return new ChannelResponse(c.getId(), c.getChannelType(), c.getConfigJson(), c.isEnabled());
    }
}
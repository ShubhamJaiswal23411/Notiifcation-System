package com.example.notificationservice.service;

import com.example.notificationservice.domain.Channel;
import com.example.notificationservice.domain.Tenant;
import com.example.notificationservice.domain.enums.ChannelType;
import com.example.notificationservice.dto.channel.CreateChannelRequest;
import com.example.notificationservice.exception.ResourceNotFoundException;
import com.example.notificationservice.repository.ChannelRepository;
import com.example.notificationservice.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ChannelServiceTest {

    @Mock private ChannelRepository channelRepository;
    @Mock private TenantRepository tenantRepository;
    @InjectMocks private ChannelService channelService;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(1L);
    }

    @Test
    void createChannel_tenantExists_savesChannel() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        Channel saved = new Channel();
        saved.setId(9L);
        saved.setTenant(tenant);
        saved.setChannelType(ChannelType.EMAIL);
        saved.setEnabled(true);
        when(channelRepository.save(any(Channel.class))).thenReturn(saved);

        CreateChannelRequest request = new CreateChannelRequest();
        request.setChannelType(ChannelType.EMAIL);
        request.setConfigJson("{}");

        var response = channelService.createChannel(1L, request);

        assertThat(response.getChannelType()).isEqualTo(ChannelType.EMAIL);
        assertThat(response.isEnabled()).isTrue();
    }

    @Test
    void createChannel_tenantNotFound_throws() {
        when(tenantRepository.findById(404L)).thenReturn(Optional.empty());

        CreateChannelRequest request = new CreateChannelRequest();
        request.setChannelType(ChannelType.SMS);

        assertThatThrownBy(() -> channelService.createChannel(404L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listChannels_returnsMappedList() {
        Channel c = new Channel();
        c.setId(1L);
        c.setChannelType(ChannelType.PUSH);
        when(channelRepository.findByTenantId(1L)).thenReturn(List.of(c));

        var result = channelService.listChannels(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getChannelType()).isEqualTo(ChannelType.PUSH);
    }
}
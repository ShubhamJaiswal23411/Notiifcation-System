package com.example.notificationservice.repository;

import com.example.notificationservice.domain.Template;
import com.example.notificationservice.domain.enums.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TemplateRepository extends JpaRepository<Template, Long> {
    List<Template> findByTenantId(Long tenantId);
    Optional<Template> findByTenantIdAndNameAndChannelType(Long tenantId, String name, ChannelType channelType);
}
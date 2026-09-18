package com.example.notificationservice.repository;

import com.example.notificationservice.domain.Notification;
import com.example.notificationservice.domain.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByIdempotencyKey(String idempotencyKey);

    List<Notification> findByTenantId(Long tenantId);

    @Query("SELECT n FROM Notification n WHERE n.status = :status AND n.scheduledAt <= :now")
    List<Notification> findDueScheduled(@Param("status") NotificationStatus status,
                                         @Param("now") OffsetDateTime now);

    @Query("SELECT n FROM Notification n WHERE n.status = :status AND n.nextRetryAt <= :now")
    List<Notification> findDueForRetry(@Param("status") NotificationStatus status,
                                        @Param("now") OffsetDateTime now);
}
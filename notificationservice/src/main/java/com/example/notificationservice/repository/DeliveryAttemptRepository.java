package com.example.notificationservice.repository;

import com.example.notificationservice.domain.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, Long> {
    List<DeliveryAttempt> findByNotificationIdOrderByAttemptNumberAsc(Long notificationId);
}
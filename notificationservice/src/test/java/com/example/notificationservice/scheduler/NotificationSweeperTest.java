package com.example.notificationservice.scheduler;

import com.example.notificationservice.domain.Notification;
import com.example.notificationservice.domain.enums.NotificationStatus;
import com.example.notificationservice.repository.NotificationRepository;
import com.example.notificationservice.service.dispatch.NotificationDispatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationSweeperTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationDispatchService dispatchService;
    @InjectMocks private NotificationSweeper sweeper;

    @Test
    void sweep_dispatchesDueScheduledAndDueRetries() {
        Notification scheduled = new Notification();
        scheduled.setId(1L);
        Notification retry = new Notification();
        retry.setId(2L);

        when(notificationRepository.findDueScheduled(eq(NotificationStatus.SCHEDULED), any()))
                .thenReturn(List.of(scheduled));
        when(notificationRepository.findDueForRetry(eq(NotificationStatus.FAILED), any()))
                .thenReturn(List.of(retry));

        sweeper.sweep();

        verify(dispatchService).dispatchAsync(1L);
        verify(dispatchService).dispatchAsync(2L);
    }

    @Test
    void sweep_nothingDue_dispatchesNothing() {
        when(notificationRepository.findDueScheduled(any(), any())).thenReturn(List.of());
        when(notificationRepository.findDueForRetry(any(), any())).thenReturn(List.of());

        sweeper.sweep();

        verify(dispatchService, never()).dispatchAsync(any());
    }
}
package com.example.notificationservice.exception;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class ResourceNotFoundExceptionTest {

    @Test
    void carriesMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Notification not found: 1");
        assertThat(ex.getMessage()).isEqualTo("Notification not found: 1");
    }
}
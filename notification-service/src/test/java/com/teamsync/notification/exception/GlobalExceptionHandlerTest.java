package com.teamsync.notification.exception;

import com.teamsync.common.dto.ApiResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GlobalExceptionHandler.
 * Tests exception handling and error response formatting.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Global Exception Handler Tests")
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    // ==================== Custom Exception Tests ====================

    @Nested
    @DisplayName("Custom Exception Tests")
    class CustomExceptionTests {

        @Test
        @DisplayName("Should handle NotificationNotFoundException")
        void handleNotificationNotFound_ReturnsNotFoundResponse() {
            // Given
            NotificationNotFoundException ex = new NotificationNotFoundException("notif-123");

            // When
            ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleNotificationNotFound(ex);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getCode()).isEqualTo("NOTIFICATION_NOT_FOUND");
            assertThat(response.getBody().getError()).contains("notif-123");
        }

        @Test
        @DisplayName("Should handle NotificationNotFoundException with tenant context")
        void handleNotificationNotFound_WithContext_ReturnsDetailedError() {
            // Given
            NotificationNotFoundException ex = new NotificationNotFoundException(
                    "notif-123", "tenant-456", "user-789");

            // When
            ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleNotificationNotFound(ex);

            // Then
            assertThat(response.getBody().getError()).contains("notif-123");
            assertThat(response.getBody().getError()).contains("tenant-456");
            assertThat(response.getBody().getError()).contains("user-789");
        }

        @Test
        @DisplayName("Should handle PreferenceNotFoundException")
        void handlePreferenceNotFound_ReturnsNotFoundResponse() {
            // Given
            PreferenceNotFoundException ex = new PreferenceNotFoundException("tenant-123", "user-456");

            // When
            ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handlePreferenceNotFound(ex);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().getCode()).isEqualTo("PREFERENCE_NOT_FOUND");
        }

        @Test
        @DisplayName("Should handle InvalidNotificationException")
        void handleInvalidNotification_ReturnsBadRequestResponse() {
            // Given
            InvalidNotificationException ex = new InvalidNotificationException("title", "cannot be empty");

            // When
            ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleInvalidNotification(ex);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getCode()).isEqualTo("INVALID_NOTIFICATION");
            assertThat(response.getBody().getError()).contains("title");
        }

        @Test
        @DisplayName("Should handle NotificationDeliveryException")
        void handleDeliveryFailure_ReturnsServiceUnavailableResponse() {
            // Given
            NotificationDeliveryException ex = new NotificationDeliveryException(
                    "notif-123", "email", "SMTP connection failed");

            // When
            ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleDeliveryFailure(ex);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().getCode()).isEqualTo("DELIVERY_FAILED");
        }

        @Test
        @DisplayName("Should handle UnauthorizedAccessException")
        void handleUnauthorizedAccess_ReturnsForbiddenResponse() {
            // Given
            UnauthorizedAccessException ex = new UnauthorizedAccessException(
                    "user-123", "notification", "notif-456");

            // When
            ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleUnauthorizedAccess(ex);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody().getCode()).isEqualTo("UNAUTHORIZED_ACCESS");
        }
    }

    // ==================== Security Exception Tests ====================

    @Nested
    @DisplayName("Security Exception Tests")
    class SecurityExceptionTests {

        @Test
        @DisplayName("Should handle AccessDeniedException")
        void handleAccessDenied_ReturnsForbiddenResponse() {
            // Given
            AccessDeniedException ex = new AccessDeniedException("Access denied");

            // When
            ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAccessDenied(ex);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody().getCode()).isEqualTo("ACCESS_DENIED");
        }
    }

    // ==================== Validation Exception Tests ====================

    @Nested
    @DisplayName("Validation Exception Tests")
    class ValidationExceptionTests {

        @Test
        @DisplayName("Should handle MethodArgumentNotValidException")
        void handleValidationException_ReturnsBadRequestWithErrors() {
            // Given
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getFieldErrors()).thenReturn(List.of(
                    new FieldError("request", "title", "must not be empty"),
                    new FieldError("request", "type", "must not be null")
            ));

            MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
            when(ex.getBindingResult()).thenReturn(bindingResult);

            // When
            ResponseEntity<ApiResponse<Map<String, String>>> response =
                    exceptionHandler.handleValidationException(ex);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
            assertThat(response.getBody().getData()).containsKeys("title", "type");
        }

        @Test
        @DisplayName("Should handle MissingRequestHeaderException")
        void handleMissingHeader_ReturnsBadRequestResponse() {
            // Given
            MissingRequestHeaderException ex = mock(MissingRequestHeaderException.class);
            when(ex.getHeaderName()).thenReturn("X-Tenant-ID");

            // When
            ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleMissingHeader(ex);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getCode()).isEqualTo("MISSING_HEADER");
            assertThat(response.getBody().getError()).contains("X-Tenant-ID");
        }
    }

    // ==================== Generic Exception Tests ====================

    @Nested
    @DisplayName("Generic Exception Tests")
    class GenericExceptionTests {

        @Test
        @DisplayName("Should handle IllegalArgumentException")
        void handleIllegalArgument_ReturnsBadRequestResponse() {
            // Given
            IllegalArgumentException ex = new IllegalArgumentException("Invalid notification ID format");

            // When
            ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleIllegalArgument(ex);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getCode()).isEqualTo("INVALID_ARGUMENT");
            assertThat(response.getBody().getError()).isEqualTo("Invalid notification ID format");
        }

        @Test
        @DisplayName("Should handle IllegalStateException")
        void handleIllegalState_ReturnsConflictResponse() {
            // Given
            IllegalStateException ex = new IllegalStateException("Notification already archived");

            // When
            ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleIllegalState(ex);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().getCode()).isEqualTo("INVALID_STATE");
        }

        @Test
        @DisplayName("Should handle generic Exception")
        void handleGenericException_ReturnsInternalServerError() {
            // Given
            Exception ex = new RuntimeException("Unexpected database error");

            // When
            ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleGenericException(ex);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
            // Should not expose internal error details
            assertThat(response.getBody().getError()).doesNotContain("database");
        }
    }

    // ==================== Exception Details Tests ====================

    @Nested
    @DisplayName("Exception Details Tests")
    class ExceptionDetailsTests {

        @Test
        @DisplayName("NotificationNotFoundException should contain notification ID")
        void notificationNotFoundException_ShouldContainNotificationId() {
            // Given
            NotificationNotFoundException ex = new NotificationNotFoundException("notif-123");

            // Then
            assertThat(ex.getNotificationId()).isEqualTo("notif-123");
            assertThat(ex.getMessage()).contains("notif-123");
        }

        @Test
        @DisplayName("InvalidNotificationException should contain field and reason")
        void invalidNotificationException_ShouldContainFieldAndReason() {
            // Given
            InvalidNotificationException ex = new InvalidNotificationException("title", "must not be blank");

            // Then
            assertThat(ex.getField()).isEqualTo("title");
            assertThat(ex.getReason()).isEqualTo("must not be blank");
        }

        @Test
        @DisplayName("NotificationDeliveryException should contain channel information")
        void notificationDeliveryException_ShouldContainChannel() {
            // Given
            NotificationDeliveryException ex = new NotificationDeliveryException(
                    "notif-123", "push", "Device token invalid");

            // Then
            assertThat(ex.getNotificationId()).isEqualTo("notif-123");
            assertThat(ex.getChannel()).isEqualTo("push");
        }

        @Test
        @DisplayName("UnauthorizedAccessException should contain resource information")
        void unauthorizedAccessException_ShouldContainResourceInfo() {
            // Given
            UnauthorizedAccessException ex = new UnauthorizedAccessException(
                    "user-123", "notification", "notif-456");

            // Then
            assertThat(ex.getUserId()).isEqualTo("user-123");
            assertThat(ex.getResourceType()).isEqualTo("notification");
            assertThat(ex.getResourceId()).isEqualTo("notif-456");
        }
    }
}

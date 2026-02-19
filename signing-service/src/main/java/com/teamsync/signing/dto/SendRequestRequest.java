package com.teamsync.signing.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for sending a signature request to signers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendRequestRequest {

    /**
     * Optional custom message to include in the email.
     */
    @Size(max = 2000, message = "Message cannot exceed 2000 characters")
    private String customMessage;

    /**
     * Whether to send reminder emails.
     */
    private Boolean sendReminders;

    /**
     * Days before expiry to send reminders.
     */
    private Integer reminderDaysBefore;
}

package com.teamsync.permission.dto;

import com.teamsync.common.model.DriveType;
import com.teamsync.permission.model.Drive.DriveSettings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to create a new drive.
 *
 * SECURITY FIX (Round 15 #M17): Added @Size constraints to prevent DoS attacks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDriveRequest {

    @NotBlank(message = "Drive name is required")
    @Size(max = 255, message = "Drive name must not exceed 255 characters")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @NotNull(message = "Drive type is required")
    private DriveType type;

    /**
     * For PERSONAL drives: the owner user ID
     */
    @Size(max = 64, message = "Owner ID must not exceed 64 characters")
    private String ownerId;

    /**
     * For DEPARTMENT drives: the department ID
     */
    @Size(max = 64, message = "Department ID must not exceed 64 characters")
    private String departmentId;

    /**
     * Storage quota in bytes (null = unlimited)
     */
    private Long quotaBytes;

    /**
     * Default role for new members (for department drives)
     */
    @Size(max = 64, message = "Default role ID must not exceed 64 characters")
    private String defaultRoleId;

    /**
     * Drive settings
     */
    private DriveSettings settings;
}

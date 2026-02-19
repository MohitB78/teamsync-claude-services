package com.teamsync.team.dto.portal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Upload response with presigned URL for portal users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalUploadResponse {

    private String uploadUrl;
    private String documentId;
    private Instant expiresAt;
}

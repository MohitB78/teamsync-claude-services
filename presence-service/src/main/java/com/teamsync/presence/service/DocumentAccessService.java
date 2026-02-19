package com.teamsync.presence.service;

import com.teamsync.presence.client.PermissionClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * SECURITY: Service to verify document access before allowing presence operations.
 * Prevents cross-tenant information disclosure through presence system.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAccessService {

    private final PermissionClient permissionClient;

    /**
     * Check if user has READ access to a document's drive.
     *
     * @param tenantId The tenant ID
     * @param userId The user ID
     * @param driveId The drive ID containing the document
     * @return true if user has access, false otherwise
     */
    public boolean hasDocumentAccess(String tenantId, String userId, String driveId) {
        if (tenantId == null || userId == null || driveId == null) {
            log.warn("SECURITY: Null parameters in access check - tenantId={}, userId={}, driveId={}",
                    tenantId, userId, driveId);
            return false;
        }

        // Personal drives are always accessible to their owner
        if (driveId.startsWith("personal-") && driveId.equals("personal-" + userId)) {
            log.debug("Access granted to personal drive for user: {}", userId);
            return true;
        }

        try {
            var response = permissionClient.checkPermissions(userId, driveId, tenantId, userId);

            if (response != null && response.getData() != null) {
                boolean hasAccess = response.getData().hasAccess();
                log.debug("Permission check for user {} on drive {}: hasAccess={}",
                        userId, driveId, hasAccess);
                return hasAccess;
            }

            log.warn("Empty response from permission service for user {} on drive {}", userId, driveId);
            return false;

        } catch (Exception e) {
            // SECURITY: Fail closed - deny access on error
            log.error("SECURITY: Permission check failed for user {} on drive {}: {}",
                    userId, driveId, e.getMessage());
            return false;
        }
    }
}

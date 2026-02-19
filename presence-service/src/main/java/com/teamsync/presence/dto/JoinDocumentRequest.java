package com.teamsync.presence.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to join a document editing session.
 *
 * SECURITY FIX (Round 7): The documentId field is set from the path variable by the
 * controller, not from the request body. This prevents parameter pollution attacks.
 * SECURITY FIX (Round 15 #M16): Added @Size constraints to prevent DoS attacks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class JoinDocumentRequest {

    /**
     * Document ID - set internally from path variable, not from request body.
     * SECURITY: This field is ignored during JSON deserialization to prevent
     * parameter pollution where the body documentId differs from the path variable.
     */
    @JsonIgnore
    private String documentId;

    @Size(max = 255, message = "Document name must not exceed 255 characters")
    private String documentName;

    @Size(max = 64, message = "Drive ID must not exceed 64 characters")
    private String driveId;

    private JoinMode mode;

    @Size(max = 32, message = "Preferred color must not exceed 32 characters")
    private String preferredColor;

    public enum JoinMode {
        VIEW,
        EDIT
    }
}

package com.teamsync.presence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating cursor position in a document.
 *
 * SECURITY FIX (Round 15 #M24): Added @Size constraint to prevent DoS attacks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateCursorRequest {

    @NotBlank(message = "Document ID is required")
    @Size(max = 64, message = "Document ID must not exceed 64 characters")
    private String documentId;

    private DocumentPresenceDTO.EditorState state;

    private DocumentPresenceDTO.CursorPosition cursorPosition;

    private DocumentPresenceDTO.SelectionRange selectionRange;
}

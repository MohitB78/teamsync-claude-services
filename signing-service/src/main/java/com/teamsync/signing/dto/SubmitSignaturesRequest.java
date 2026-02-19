package com.teamsync.signing.dto;

import com.teamsync.signing.model.SigningTemplate.FieldType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for submitting signatures.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitSignaturesRequest {

    @NotEmpty(message = "At least one field value is required")
    @Valid
    private List<FieldValueRequest> fieldValues;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldValueRequest {

        @NotNull(message = "Field ID is required")
        private String fieldId;

        @NotNull(message = "Field type is required")
        private FieldType type;

        /**
         * For SIGNATURE and INITIALS: Base64-encoded image data.
         * For TEXT: Plain text value.
         * For DATE: ISO date string.
         * For CHECKBOX: "true" or "false".
         */
        @NotNull(message = "Value is required")
        private String value;

        /**
         * Signature method: DRAW, TYPE, or UPLOAD.
         */
        private String signatureMethod;
    }
}

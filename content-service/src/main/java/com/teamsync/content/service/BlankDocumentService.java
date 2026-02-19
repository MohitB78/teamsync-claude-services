package com.teamsync.content.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.common.model.Permission;
import com.teamsync.common.permission.RequiresPermission;
import com.teamsync.common.storage.CloudStorageProvider;
import com.teamsync.content.dto.document.CreateDocumentRequest;
import com.teamsync.content.dto.document.DocumentDTO;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service for creating blank Office documents (Word, Excel, PowerPoint).
 *
 * <p>This service provides the ability to create new blank documents from
 * minimal template files. The templates are stored as classpath resources
 * and are uploaded to cloud storage when a user requests a new document.</p>
 *
 * <h2>Supported Document Types</h2>
 * <ul>
 *   <li><b>Word (.docx)</b>: application/vnd.openxmlformats-officedocument.wordprocessingml.document</li>
 *   <li><b>Excel (.xlsx)</b>: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet</li>
 *   <li><b>PowerPoint (.pptx)</b>: application/vnd.openxmlformats-officedocument.presentationml.presentation</li>
 * </ul>
 *
 * @author TeamSync Platform Team
 * @since 1.0.0
 */
@Service
@Slf4j
public class BlankDocumentService {

    private final CloudStorageProvider storageProvider;
    private final DocumentService documentService;

    /**
     * SECURITY: Maximum allowed filename length to prevent buffer issues.
     */
    private static final int MAX_FILENAME_LENGTH = 200;

    /**
     * SECURITY: Pattern for validating filenames.
     * Blocks: path traversal (..), control characters, and invalid filesystem characters.
     */
    private static final Pattern VALID_FILENAME_PATTERN =
            Pattern.compile("^(?!.*\\.\\.)(?!\\.)[^/\\\\:*?\"<>|\\x00-\\x1f]+$");

    /**
     * SECURITY: Pattern for validating tenant/drive IDs.
     */
    private static final Pattern VALID_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9-]{1,64}$");

    /**
     * Metrics for monitoring blank document creation.
     */
    private final Counter blankDocumentCreatedCounter;
    private final Counter blankDocumentFailedCounter;

    @Value("${teamsync.storage.default-bucket:teamsync-documents}")
    private String defaultBucket;

    public BlankDocumentService(
            CloudStorageProvider storageProvider,
            DocumentService documentService,
            MeterRegistry meterRegistry) {
        this.storageProvider = storageProvider;
        this.documentService = documentService;

        // Register metrics for monitoring
        this.blankDocumentCreatedCounter = Counter.builder("teamsync.documents.blank.created")
                .description("Number of blank documents created")
                .tag("service", "content-service")
                .register(meterRegistry);

        this.blankDocumentFailedCounter = Counter.builder("teamsync.documents.blank.failed")
                .description("Number of failed blank document creation attempts")
                .tag("service", "content-service")
                .register(meterRegistry);
    }

    /**
     * Supported blank document types.
     */
    public enum BlankDocumentType {
        WORD("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "templates/blank.docx"),
        EXCEL("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "templates/blank.xlsx"),
        POWERPOINT("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "templates/blank.pptx");

        private final String extension;
        private final String contentType;
        private final String templatePath;

        BlankDocumentType(String extension, String contentType, String templatePath) {
            this.extension = extension;
            this.contentType = contentType;
            this.templatePath = templatePath;
        }

        public String getExtension() {
            return extension;
        }

        public String getContentType() {
            return contentType;
        }

        public String getTemplatePath() {
            return templatePath;
        }

        /**
         * Get blank document type from string (case-insensitive).
         *
         * <p>SECURITY: Only accepts predefined type names to prevent injection.
         * The type is validated before use and never passed to file paths or commands.</p>
         *
         * @param type The document type string (word, excel, powerpoint, docx, xlsx, pptx)
         * @return The corresponding BlankDocumentType enum value
         * @throws IllegalArgumentException if type is null, empty, or unsupported
         */
        public static BlankDocumentType fromString(String type) {
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("Document type cannot be null or empty");
            }

            // SECURITY: Limit length to prevent potential DoS via regex
            if (type.length() > 20) {
                throw new IllegalArgumentException("Invalid document type");
            }

            return switch (type.toLowerCase().trim()) {
                case "word", "docx" -> WORD;
                case "excel", "xlsx" -> EXCEL;
                case "powerpoint", "pptx" -> POWERPOINT;
                // SECURITY: Don't expose the invalid type value in the error message
                // to prevent information disclosure about what values are checked
                default -> throw new IllegalArgumentException("Unsupported document type");
            };
        }
    }

    /**
     * Creates a blank document of the specified type.
     *
     * <p>This method:</p>
     * <ol>
     *   <li>Validates input parameters for security</li>
     *   <li>Loads the blank template from classpath resources</li>
     *   <li>Uploads the template to cloud storage with a unique key</li>
     *   <li>Creates the document metadata record</li>
     *   <li>Returns the created document for immediate editing</li>
     * </ol>
     *
     * <h3>Security Measures</h3>
     * <ul>
     *   <li>Validates filename against path traversal and injection attacks</li>
     *   <li>Validates tenant/drive/user IDs from context</li>
     *   <li>Limits filename length to prevent buffer issues</li>
     *   <li>Only allows predefined template types (no arbitrary file paths)</li>
     *   <li>Uses UUID in storage key to prevent overwrites</li>
     * </ul>
     *
     * @param type     The type of blank document to create (WORD, EXCEL, POWERPOINT)
     * @param name     The name for the new document (without extension)
     * @param folderId The folder to create the document in (null for root)
     * @return The created document DTO
     * @throws IllegalArgumentException if the document type is not supported or validation fails
     * @throws ResourceNotFoundException if the template file cannot be found
     * @throws RuntimeException if upload or creation fails
     */
    @RequiresPermission(Permission.WRITE)
    @Transactional
    public DocumentDTO createBlankDocument(BlankDocumentType type, String name, String folderId) {
        String tenantId = TenantContext.getTenantId();
        String driveId = TenantContext.getDriveId();
        String userId = TenantContext.getUserId();

        // SECURITY: Validate tenant context is present
        if (tenantId == null || tenantId.isBlank()) {
            log.warn("SECURITY: Blank document creation attempted without tenant context");
            blankDocumentFailedCounter.increment();
            throw new IllegalArgumentException("Tenant context is required");
        }

        if (driveId == null || driveId.isBlank()) {
            log.warn("SECURITY: Blank document creation attempted without drive context");
            blankDocumentFailedCounter.increment();
            throw new IllegalArgumentException("Drive context is required");
        }

        // SECURITY: Validate tenant and drive IDs format
        if (!VALID_ID_PATTERN.matcher(tenantId).matches()) {
            log.warn("SECURITY: Invalid tenant ID format: {}", tenantId.substring(0, Math.min(tenantId.length(), 20)));
            blankDocumentFailedCounter.increment();
            throw new IllegalArgumentException("Invalid tenant ID format");
        }

        if (!VALID_ID_PATTERN.matcher(driveId).matches()) {
            log.warn("SECURITY: Invalid drive ID format: {}", driveId.substring(0, Math.min(driveId.length(), 20)));
            blankDocumentFailedCounter.increment();
            throw new IllegalArgumentException("Invalid drive ID format");
        }

        log.info("Creating blank {} document '{}' in folder {} for tenant {}",
                type.name(), name, folderId, tenantId);

        // SECURITY: Sanitize and validate filename
        String sanitizedName = sanitizeFilename(name);
        String fullName = ensureExtension(sanitizedName, type.getExtension());

        // SECURITY: Validate folderId format if provided
        if (folderId != null && !folderId.isBlank() && !VALID_ID_PATTERN.matcher(folderId).matches()) {
            log.warn("SECURITY: Invalid folder ID format attempted: {}", folderId.substring(0, Math.min(folderId.length(), 20)));
            blankDocumentFailedCounter.increment();
            throw new IllegalArgumentException("Invalid folder ID format");
        }

        // Load template from classpath
        byte[] templateContent = loadTemplate(type);

        // Generate unique storage key
        String storageKey = generateStorageKey(tenantId, driveId, fullName);

        // Upload template to storage
        try (InputStream inputStream = new ByteArrayInputStream(templateContent)) {
            storageProvider.upload(
                    defaultBucket,
                    storageKey,
                    inputStream,
                    templateContent.length,
                    type.getContentType(),
                    Map.of(
                            "tenantId", tenantId,
                            "driveId", driveId,
                            "createdBy", userId,
                            "isBlankDocument", "true"
                    )
            );
            log.debug("Uploaded blank document template to {}/{}", defaultBucket, storageKey);
        } catch (IOException e) {
            log.error("Failed to upload blank document template", e);
            blankDocumentFailedCounter.increment();
            throw new RuntimeException("Failed to create blank document: upload failed", e);
        }

        // Create document metadata
        CreateDocumentRequest request = CreateDocumentRequest.builder()
                .name(fullName)
                .description("New " + type.name().toLowerCase() + " document")
                .folderId(folderId)
                .contentType(type.getContentType())
                .fileSize((long) templateContent.length)
                .storageKey(storageKey)
                .storageBucket(defaultBucket)
                .metadata(Map.of(
                        "isBlankDocument", true,
                        "templateType", type.name()
                ))
                .tags(List.of())
                .build();

        DocumentDTO document = documentService.createDocument(request);

        // Record success metric
        blankDocumentCreatedCounter.increment();

        log.info("Created blank {} document: {} ({})", type.name(), document.getId(), fullName);

        return document;
    }

    /**
     * SECURITY: Sanitizes a filename to prevent path traversal and injection attacks.
     *
     * <p>This method:</p>
     * <ul>
     *   <li>Removes control characters</li>
     *   <li>Validates against path traversal sequences</li>
     *   <li>Limits length to prevent buffer issues</li>
     *   <li>Provides a default name if sanitization results in empty string</li>
     * </ul>
     *
     * @param name The original filename
     * @return A sanitized filename safe for use in storage paths
     */
    private String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "Untitled";
        }

        String sanitized = name.trim();

        // Remove control characters
        sanitized = sanitized.replaceAll("[\\x00-\\x1f\\x7f]", "");

        // Validate against dangerous patterns
        if (!VALID_FILENAME_PATTERN.matcher(sanitized).matches()) {
            log.warn("SECURITY: Invalid filename rejected: {}", name.substring(0, Math.min(name.length(), 50)));
            throw new IllegalArgumentException("Invalid filename: contains prohibited characters");
        }

        // Limit length
        if (sanitized.length() > MAX_FILENAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_FILENAME_LENGTH);
        }

        // Ensure we still have a valid name after sanitization
        if (sanitized.isBlank()) {
            return "Untitled";
        }

        return sanitized;
    }

    /**
     * Loads the blank template file from classpath resources.
     *
     * @param type The document type
     * @return The template file content as byte array
     * @throws ResourceNotFoundException if template file not found
     */
    private byte[] loadTemplate(BlankDocumentType type) {
        try {
            ClassPathResource resource = new ClassPathResource(type.getTemplatePath());
            if (!resource.exists()) {
                log.error("Template file not found: {}", type.getTemplatePath());
                throw new ResourceNotFoundException("Template not found for type: " + type.name());
            }

            try (InputStream is = resource.getInputStream()) {
                return is.readAllBytes();
            }
        } catch (IOException e) {
            log.error("Failed to load template file: {}", type.getTemplatePath(), e);
            throw new RuntimeException("Failed to load template: " + type.name(), e);
        }
    }

    /**
     * Ensures the filename has the correct extension.
     */
    private String ensureExtension(String name, String extension) {
        if (name == null || name.isBlank()) {
            return "Untitled." + extension;
        }
        String trimmedName = name.trim();
        if (trimmedName.toLowerCase().endsWith("." + extension)) {
            return trimmedName;
        }
        // Remove any existing extension and add the correct one
        int dotIndex = trimmedName.lastIndexOf('.');
        if (dotIndex > 0) {
            trimmedName = trimmedName.substring(0, dotIndex);
        }
        return trimmedName + "." + extension;
    }

    /**
     * Generates a unique storage key for the document.
     */
    private String generateStorageKey(String tenantId, String driveId, String filename) {
        // Format: {tenantId}/{driveId}/documents/{year}/{month}/{uuid}-{filename}
        Instant now = Instant.now();
        String year = String.valueOf(java.time.Year.now().getValue());
        String month = String.format("%02d", java.time.MonthDay.now().getMonthValue());
        String uuid = UUID.randomUUID().toString().substring(0, 8);

        return String.format("%s/%s/documents/%s/%s/%s-%s",
                tenantId, driveId, year, month, uuid, filename);
    }
}

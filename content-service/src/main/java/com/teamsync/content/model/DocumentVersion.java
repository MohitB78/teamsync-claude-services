package com.teamsync.content.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "document_versions")
@CompoundIndexes({
        @CompoundIndex(name = "document_version_idx", def = "{'documentId': 1, 'versionNumber': -1}"),
        @CompoundIndex(name = "tenant_document_idx", def = "{'tenantId': 1, 'documentId': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVersion {

    @Id
    private String id;

    @Field("tenantId")
    private String tenantId;

    @Field("documentId")
    private String documentId;

    @Field("versionNumber")
    private Integer versionNumber;

    @Field("name")
    private String name;

    @Field("contentType")
    private String contentType;

    @Field("fileSize")
    private Long fileSize;

    @Field("storageKey")
    private String storageKey;

    @Field("storageBucket")
    private String storageBucket;

    @Field("checksum")
    private String checksum;

    @Field("comment")
    private String comment;  // Version comment/description

    @Field("createdBy")
    private String createdBy;

    @Field("createdByName")
    private String createdByName;

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    @Field("isCurrent")
    @Builder.Default
    private Boolean isCurrent = false;
}

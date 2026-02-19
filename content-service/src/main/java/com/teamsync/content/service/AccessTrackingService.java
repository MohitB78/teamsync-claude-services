package com.teamsync.content.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.content.repository.DocumentRepository;
import com.teamsync.content.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

/**
 * Service for tracking document and folder access asynchronously.
 *
 * <h2>Performance Optimization</h2>
 * <p>Instead of writing to MongoDB on every read (which causes write amplification),
 * this service buffers access events in Redis and flushes them to MongoDB in batches
 * every 5 minutes.</p>
 *
 * <h2>Redis Data Structure</h2>
 * <p>Uses Redis sorted sets with timestamp as score:</p>
 * <ul>
 *   <li>Key: {@code teamsync:access:documents} or {@code teamsync:access:folders}</li>
 *   <li>Member: {@code tenantId:driveId:resourceId}</li>
 *   <li>Score: Unix timestamp of last access</li>
 * </ul>
 *
 * <h2>Flush Behavior</h2>
 * <p>Every 5 minutes, the scheduled job:</p>
 * <ol>
 *   <li>Reads all entries from the sorted set</li>
 *   <li>Batch updates accessedAt in MongoDB</li>
 *   <li>Clears the processed entries from Redis</li>
 * </ol>
 *
 * @author TeamSync Platform Team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccessTrackingService {

    private final StringRedisTemplate redisTemplate;
    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;

    private static final String DOCUMENT_ACCESS_KEY = "teamsync:access:documents";
    private static final String FOLDER_ACCESS_KEY = "teamsync:access:folders";

    /**
     * Records a document access event asynchronously.
     *
     * <p>Buffers the access in Redis instead of writing directly to MongoDB.
     * The access will be flushed to MongoDB within 5 minutes.</p>
     *
     * @param tenantId   the tenant ID
     * @param driveId    the drive ID
     * @param documentId the document ID
     * @param accessTime the time of access
     */
    public void recordDocumentAccess(String tenantId, String driveId, String documentId, Instant accessTime) {
        try {
            String member = tenantId + ":" + driveId + ":" + documentId;
            double score = accessTime.toEpochMilli();
            redisTemplate.opsForZSet().add(DOCUMENT_ACCESS_KEY, member, score);
            log.trace("Buffered document access: {}", member);
        } catch (Exception e) {
            // Don't fail the read operation if Redis is unavailable
            log.warn("Failed to buffer document access event: {}", e.getMessage());
        }
    }

    /**
     * Records a folder access event asynchronously.
     *
     * <p>Buffers the access in Redis instead of writing directly to MongoDB.
     * The access will be flushed to MongoDB within 5 minutes.</p>
     *
     * @param tenantId   the tenant ID
     * @param driveId    the drive ID
     * @param folderId   the folder ID
     * @param accessTime the time of access
     */
    public void recordFolderAccess(String tenantId, String driveId, String folderId, Instant accessTime) {
        try {
            String member = tenantId + ":" + driveId + ":" + folderId;
            double score = accessTime.toEpochMilli();
            redisTemplate.opsForZSet().add(FOLDER_ACCESS_KEY, member, score);
            log.trace("Buffered folder access: {}", member);
        } catch (Exception e) {
            // Don't fail the read operation if Redis is unavailable
            log.warn("Failed to buffer folder access event: {}", e.getMessage());
        }
    }

    /**
     * Flushes buffered document access events to MongoDB.
     *
     * <p>Runs every 5 minutes via Spring scheduling. Processes all buffered
     * document access events and updates accessedAt in MongoDB.</p>
     *
     * SECURITY FIX (Round 15 #C10): Clear TenantContext after scheduled job execution
     * to prevent context leakage in virtual thread pools.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void flushDocumentAccessUpdates() {
        try {
            Set<String> members = redisTemplate.opsForZSet().range(DOCUMENT_ACCESS_KEY, 0, -1);
            if (members == null || members.isEmpty()) {
                return;
            }

            log.info("Flushing {} document access updates to MongoDB", members.size());
            int successCount = 0;
            int failCount = 0;

            for (String member : members) {
                try {
                    Double score = redisTemplate.opsForZSet().score(DOCUMENT_ACCESS_KEY, member);
                    if (score == null) continue;

                    String[] parts = member.split(":", 3);
                    if (parts.length != 3) {
                        log.warn("Invalid document access member format: {}", member);
                        continue;
                    }

                    String tenantId = parts[0];
                    String driveId = parts[1];
                    String documentId = parts[2];
                    Instant accessTime = Instant.ofEpochMilli(score.longValue());

                    // Update accessedAt in MongoDB
                    documentRepository.findByIdAndTenantIdAndDriveId(documentId, tenantId, driveId)
                            .ifPresent(doc -> {
                                doc.setAccessedAt(accessTime);
                                documentRepository.save(doc);
                            });

                    // Remove from Redis after successful update
                    redisTemplate.opsForZSet().remove(DOCUMENT_ACCESS_KEY, member);
                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to flush document access for {}: {}", member, e.getMessage());
                    failCount++;
                }
            }

            log.info("Document access flush complete: {} succeeded, {} failed", successCount, failCount);
        } catch (Exception e) {
            log.error("Failed to flush document access updates: {}", e.getMessage());
        } finally {
            // SECURITY FIX (Round 15 #C10): Always clear TenantContext to prevent leakage
            TenantContext.clear();
        }
    }

    /**
     * Flushes buffered folder access events to MongoDB.
     *
     * <p>Runs every 5 minutes via Spring scheduling. Processes all buffered
     * folder access events and updates accessedAt in MongoDB.</p>
     *
     * SECURITY FIX (Round 15 #C10): Clear TenantContext after scheduled job execution
     * to prevent context leakage in virtual thread pools.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void flushFolderAccessUpdates() {
        try {
            Set<String> members = redisTemplate.opsForZSet().range(FOLDER_ACCESS_KEY, 0, -1);
            if (members == null || members.isEmpty()) {
                return;
            }

            log.info("Flushing {} folder access updates to MongoDB", members.size());
            int successCount = 0;
            int failCount = 0;

            for (String member : members) {
                try {
                    Double score = redisTemplate.opsForZSet().score(FOLDER_ACCESS_KEY, member);
                    if (score == null) continue;

                    String[] parts = member.split(":", 3);
                    if (parts.length != 3) {
                        log.warn("Invalid folder access member format: {}", member);
                        continue;
                    }

                    String tenantId = parts[0];
                    String driveId = parts[1];
                    String folderId = parts[2];
                    Instant accessTime = Instant.ofEpochMilli(score.longValue());

                    // Update accessedAt in MongoDB
                    folderRepository.findByIdAndTenantIdAndDriveId(folderId, tenantId, driveId)
                            .ifPresent(folder -> {
                                folder.setAccessedAt(accessTime);
                                folderRepository.save(folder);
                            });

                    // Remove from Redis after successful update
                    redisTemplate.opsForZSet().remove(FOLDER_ACCESS_KEY, member);
                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to flush folder access for {}: {}", member, e.getMessage());
                    failCount++;
                }
            }

            log.info("Folder access flush complete: {} succeeded, {} failed", successCount, failCount);
        } catch (Exception e) {
            log.error("Failed to flush folder access updates: {}", e.getMessage());
        } finally {
            // SECURITY FIX (Round 15 #C10): Always clear TenantContext to prevent leakage
            TenantContext.clear();
        }
    }
}

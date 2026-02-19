package com.teamsync.common.context;

/**
 * Thread-local holder for tenant context information.
 * Used for multi-tenant operations to track the current tenant.
 * Set by TenantContextFilter and accessible throughout the request.
 */
public class TenantContext {

    private static final ThreadLocal<String> currentTenantId = new ThreadLocal<>();
    private static final ThreadLocal<String> currentUserId = new ThreadLocal<>();
    private static final ThreadLocal<String> currentDriveId = new ThreadLocal<>();
    private static final ThreadLocal<String> currentRequestId = new ThreadLocal<>();

    private TenantContext() {
        // Utility class
    }

    public static String getTenantId() {
        return currentTenantId.get();
    }

    public static void setTenantId(String tenantId) {
        currentTenantId.set(tenantId);
    }

    public static String getUserId() {
        return currentUserId.get();
    }

    public static void setUserId(String userId) {
        currentUserId.set(userId);
    }

    public static String getDriveId() {
        return currentDriveId.get();
    }

    public static void setDriveId(String driveId) {
        currentDriveId.set(driveId);
    }

    public static String getRequestId() {
        return currentRequestId.get();
    }

    public static void setRequestId(String requestId) {
        currentRequestId.set(requestId);
    }

    public static void clear() {
        currentTenantId.remove();
        currentUserId.remove();
        currentDriveId.remove();
        currentRequestId.remove();
    }

    /**
     * Set all context values at once.
     */
    public static void setContext(String tenantId, String userId, String driveId) {
        setTenantId(tenantId);
        setUserId(userId);
        setDriveId(driveId);
    }

    /**
     * Create a snapshot of the current context for async operations.
     * Use with restore() to propagate context across thread boundaries.
     */
    public static TenantContextSnapshot snapshot() {
        return new TenantContextSnapshot(
                getTenantId(),
                getUserId(),
                getDriveId(),
                getRequestId()
        );
    }

    /**
     * Restore context from a snapshot. Use in async callbacks or scheduled tasks.
     */
    public static void restore(TenantContextSnapshot snapshot) {
        if (snapshot != null) {
            setTenantId(snapshot.tenantId());
            setUserId(snapshot.userId());
            setDriveId(snapshot.driveId());
            setRequestId(snapshot.requestId());
        }
    }

    /**
     * Immutable snapshot of tenant context for async propagation.
     */
    public record TenantContextSnapshot(
            String tenantId,
            String userId,
            String driveId,
            String requestId
    ) {}
}

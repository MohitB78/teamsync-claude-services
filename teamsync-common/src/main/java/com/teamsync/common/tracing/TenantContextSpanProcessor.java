package com.teamsync.common.tracing;

import com.teamsync.common.context.TenantContext;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import lombok.extern.slf4j.Slf4j;

/**
 * SpanProcessor that adds TeamSync business context to all spans.
 *
 * <p>This processor extracts tenant, user, drive, and request context from
 * the TenantContext ThreadLocal and adds them as span attributes. This enables:</p>
 * <ul>
 *   <li>Filtering traces by tenant in Kibana/Grafana</li>
 *   <li>Correlating user actions across services</li>
 *   <li>Debugging drive-level permission issues</li>
 *   <li>Linking traces to specific request IDs</li>
 * </ul>
 *
 * <p>Attributes added to each span:</p>
 * <ul>
 *   <li>{@code teamsync.tenant.id} - The tenant ID</li>
 *   <li>{@code teamsync.user.id} - The user ID</li>
 *   <li>{@code teamsync.drive.id} - The drive ID</li>
 *   <li>{@code teamsync.request.id} - The request correlation ID</li>
 * </ul>
 */
@Slf4j
public class TenantContextSpanProcessor implements SpanProcessor {

    // Custom attribute keys with teamsync namespace
    private static final AttributeKey<String> TENANT_ID = AttributeKey.stringKey("teamsync.tenant.id");
    private static final AttributeKey<String> USER_ID = AttributeKey.stringKey("teamsync.user.id");
    private static final AttributeKey<String> DRIVE_ID = AttributeKey.stringKey("teamsync.drive.id");
    private static final AttributeKey<String> REQUEST_ID = AttributeKey.stringKey("teamsync.request.id");

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        // Extract context from ThreadLocal (set by TenantContextFilter in API Gateway)
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();
        String driveId = TenantContext.getDriveId();
        String requestId = TenantContext.getRequestId();

        // Add non-null attributes to the span
        if (tenantId != null && !tenantId.isBlank()) {
            span.setAttribute(TENANT_ID, tenantId);
        }
        if (userId != null && !userId.isBlank()) {
            span.setAttribute(USER_ID, userId);
        }
        if (driveId != null && !driveId.isBlank()) {
            span.setAttribute(DRIVE_ID, driveId);
        }
        if (requestId != null && !requestId.isBlank()) {
            span.setAttribute(REQUEST_ID, requestId);
        }
    }

    @Override
    public boolean isStartRequired() {
        // We need to be called on span start to add attributes
        return true;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        // No action needed on span end
    }

    @Override
    public boolean isEndRequired() {
        // We don't need to be called on span end
        return false;
    }
}

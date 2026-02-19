package com.teamsync.common.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * Utility class for common tracing operations in TeamSync services.
 *
 * <p>Provides helper methods for:</p>
 * <ul>
 *   <li>Creating and managing spans</li>
 *   <li>Extracting trace IDs for logging</li>
 *   <li>Wrapping operations with automatic span management</li>
 *   <li>Recording exceptions in spans</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Get current trace ID for logging
 * String traceId = TracingUtils.getCurrentTraceId();
 * log.info("Processing document, traceId={}", traceId);
 *
 * // Wrap an operation with a span
 * Document doc = TracingUtils.withSpan(tracer, "processDocument", () -> {
 *     return documentService.process(request);
 * });
 * }</pre>
 */
@Slf4j
public final class TracingUtils {

    private TracingUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Gets the current trace ID from the active span.
     *
     * @return The trace ID as a hex string, or "no-trace" if no active span
     */
    public static String getCurrentTraceId() {
        Span currentSpan = Span.current();
        SpanContext spanContext = currentSpan.getSpanContext();

        if (spanContext.isValid()) {
            return spanContext.getTraceId();
        }
        return "no-trace";
    }

    /**
     * Gets the current span ID from the active span.
     *
     * @return The span ID as a hex string, or "no-span" if no active span
     */
    public static String getCurrentSpanId() {
        Span currentSpan = Span.current();
        SpanContext spanContext = currentSpan.getSpanContext();

        if (spanContext.isValid()) {
            return spanContext.getSpanId();
        }
        return "no-span";
    }

    /**
     * Executes a supplier within a new span, automatically handling span lifecycle.
     *
     * @param tracer The tracer to use for creating spans
     * @param spanName The name of the span
     * @param supplier The operation to execute
     * @param <T> The return type
     * @return The result of the supplier
     */
    public static <T> T withSpan(Tracer tracer, String spanName, Supplier<T> supplier) {
        Span span = tracer.spanBuilder(spanName).startSpan();

        try (Scope scope = span.makeCurrent()) {
            T result = supplier.get();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Executes a runnable within a new span, automatically handling span lifecycle.
     *
     * @param tracer The tracer to use for creating spans
     * @param spanName The name of the span
     * @param runnable The operation to execute
     */
    public static void withSpan(Tracer tracer, String spanName, Runnable runnable) {
        Span span = tracer.spanBuilder(spanName).startSpan();

        try (Scope scope = span.makeCurrent()) {
            runnable.run();
            span.setStatus(StatusCode.OK);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Adds a document-related attribute to the current span.
     *
     * @param documentId The document ID
     */
    public static void setDocumentId(String documentId) {
        if (documentId != null) {
            Span.current().setAttribute("teamsync.document.id", documentId);
        }
    }

    /**
     * Adds a folder-related attribute to the current span.
     *
     * @param folderId The folder ID
     */
    public static void setFolderId(String folderId) {
        if (folderId != null) {
            Span.current().setAttribute("teamsync.folder.id", folderId);
        }
    }

    /**
     * Adds an operation type attribute to the current span.
     *
     * @param operation The operation type (e.g., "upload", "download", "delete")
     */
    public static void setOperation(String operation) {
        if (operation != null) {
            Span.current().setAttribute("teamsync.operation", operation);
        }
    }

    /**
     * Records an exception in the current span without changing span status.
     *
     * @param exception The exception to record
     */
    public static void recordException(Throwable exception) {
        Span.current().recordException(exception);
    }

    /**
     * Sets the current span status to error with a message.
     *
     * @param message The error message
     */
    public static void setError(String message) {
        Span.current().setStatus(StatusCode.ERROR, message);
    }

    /**
     * Adds a custom attribute to the current span.
     *
     * @param key The attribute key
     * @param value The attribute value
     */
    public static void setAttribute(String key, String value) {
        if (key != null && value != null) {
            Span.current().setAttribute(key, value);
        }
    }

    /**
     * Adds a custom attribute to the current span.
     *
     * @param key The attribute key
     * @param value The attribute value
     */
    public static void setAttribute(String key, long value) {
        if (key != null) {
            Span.current().setAttribute(key, value);
        }
    }

    /**
     * Checks if there is an active valid span.
     *
     * @return true if there is a valid active span
     */
    public static boolean hasActiveSpan() {
        return Span.current().getSpanContext().isValid();
    }

    /**
     * Gets the current context for propagation to async operations.
     *
     * @return The current OpenTelemetry context
     */
    public static Context getCurrentContext() {
        return Context.current();
    }

    /**
     * Wraps a runnable to propagate the current trace context to another thread.
     *
     * @param runnable The runnable to wrap
     * @return A wrapped runnable that preserves trace context
     */
    public static Runnable wrapWithContext(Runnable runnable) {
        Context current = Context.current();
        return () -> {
            try (Scope scope = current.makeCurrent()) {
                runnable.run();
            }
        };
    }

    /**
     * Wraps a supplier to propagate the current trace context to another thread.
     *
     * @param supplier The supplier to wrap
     * @param <T> The return type
     * @return A wrapped supplier that preserves trace context
     */
    public static <T> Supplier<T> wrapWithContext(Supplier<T> supplier) {
        Context current = Context.current();
        return () -> {
            try (Scope scope = current.makeCurrent()) {
                return supplier.get();
            }
        };
    }
}

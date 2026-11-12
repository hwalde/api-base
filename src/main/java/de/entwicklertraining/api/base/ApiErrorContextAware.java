package de.entwicklertraining.api.base;

/**
 * Implemented by exception classes that can carry the {@link ApiErrorContext} of the HTTP
 * exchange that caused them.
 * <p>
 * Every status-code exception api-base ships implements this interface; client libraries
 * that register their own exception classes via
 * {@code registerStatusCodeException(int, Class, String, boolean)} can implement it too
 * (or declare a {@code (String, ApiErrorContext)} constructor) to receive the error body
 * and response headers from the transport.
 */
public interface ApiErrorContextAware {

    /**
     * Returns the HTTP error context recorded on this exception, or {@code null} when the
     * exception was created without one.
     *
     * @return the error context, or {@code null}
     */
    ApiErrorContext getApiErrorContext();
}

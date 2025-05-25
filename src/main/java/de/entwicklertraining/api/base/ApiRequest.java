package de.entwicklertraining.api.base;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Abstract base class representing an API request with support for various HTTP methods,
 * headers, and response handling. This class serves as the foundation for all API requests
 * in the client library.
 *
 * <p>Subclasses must implement the following methods:
 * <ul>
 *   <li>{@link #getRelativeUrl()} - The relative endpoint path</li>
 *   <li>{@link #getHttpMethod()} - The HTTP method (e.g., "GET", "POST")</li>
 *   <li>{@link #getBody()} - The request payload (e.g., JSON string)</li>
 *   <li>{@link #createResponse(String)} - Factory method for response objects</li>
 * </ul>
 *
 * <p>Features include:
 * <ul>
 *   <li>Configurable execution timeouts</li>
 *   <li>Support for custom headers</li>
 *   <li>Request cancellation support</li>
 *   <li>Optional request/response capture for debugging</li>
 *   <li>Support for both text and binary payloads</li>
 * </ul>
 *
 * @param <R> The type of ApiResponse this request will return
 */
public abstract class ApiRequest<R extends ApiResponse<?>> {

    /** Maximum allowed execution time in seconds (0 for no timeout) */
    private final int maxExecutionTimeInSeconds;

    /** Additional HTTP headers to include in the request */
    private final Map<String, String> additionalHeaders = new HashMap<>();

    /** Callback for capturing successful request data (optional) */
    private final Consumer<ApiCallCaptureInput> captureOnSuccess;

    /** Callback for capturing error data (optional) */
    private final Consumer<ApiCallCaptureInput> captureOnError;

    /** Flag indicating if the request has been canceled */
    private volatile boolean canceled = false;

    /** Supplier to check if the request has been canceled */
    private final Supplier<Boolean> isCanceledSupplier;

    protected ApiRequest(ApiRequestBuilderBase<?, ?> builderBase) {
        this.maxExecutionTimeInSeconds = builderBase.maxExecutionTimeInSeconds;
        this.captureOnSuccess = builderBase.captureOnSuccess;
        this.captureOnError = builderBase.captureOnError;
        this.isCanceledSupplier = builderBase.isCanceledSupplier;
    }

    /**
     * Gets the maximum allowed execution time in seconds.
     *
     * @return Maximum execution time in seconds, or 0 for no timeout
     */
    public int getMaxExecutionTimeInSeconds() {
        return maxExecutionTimeInSeconds;
    }

    /**
     * Checks if success capture is enabled for this request.
     *
     * @return true if success capture is enabled, false otherwise
     */
    public boolean hasCaptureOnSuccess() {
        return captureOnSuccess != null;
    }

    /**
     * Checks if error capture is enabled for this request.
     *
     * @return true if error capture is enabled, false otherwise
     */
    public boolean hasCaptureOnError() {
        return captureOnError != null;
    }

    /**
     * Gets the success capture consumer if enabled.
     *
     * @return The success capture consumer, or null if not enabled
     */
    public Consumer<ApiCallCaptureInput> getCaptureOnSuccess() {
        return captureOnSuccess;
    }

    /**
     * Gets the error capture consumer if enabled.
     *
     * @return The error capture consumer, or null if not enabled
     */
    public Consumer<ApiCallCaptureInput> getCaptureOnError() {
        return captureOnError;
    }

    /**
     * Gets the supplier that checks if this request has been canceled.
     *
     * @return A supplier that returns true if the request was canceled
     */
    public Supplier<Boolean> getIsCanceledSupplier() {
        return isCanceledSupplier;
    }

    /**
     * Gets the relative URL path for this API request.
     * This should include the path and any query parameters, but not the base URL.
     *
     * @return The relative URL path (e.g., "/api/resource?param=value")
     * @implSpec Must be implemented by subclasses to provide the endpoint path
     */
    public abstract String getRelativeUrl();

    /**
     * Gets the HTTP method for this request.
     *
     * @return The HTTP method as a string (e.g., "GET", "POST", "PUT", "DELETE")
     * @implSpec Must be implemented by subclasses to specify the HTTP method
     */
    public abstract String getHttpMethod();

    /**
     * Gets the request body content.
     *
     * @return The request body as a string (e.g., JSON), or null for requests without a body
     * @implSpec Must be implemented by subclasses for requests that include a body.
     *           For binary content, override {@link #getBodyBytes()} instead.
     */
    public abstract String getBody();

    /**
     * Creates a response object from the given response body.
     *
     * @param responseBody The raw response body as received from the server
     * @return A new response object of type R
     * @throws Exception if the response cannot be parsed or is invalid
     * @implSpec Must be implemented by subclasses to parse the response body
     *           and return an appropriate response object.
     */
    public abstract R createResponse(String responseBody);

    /**
     * Indicates whether this request expects a binary response.
     *
     * @return true if the response should be treated as binary data, false for text
     * @implNote The default implementation returns false. Override this method
     *           and return true for binary responses (e.g., file downloads).
     */
    public boolean isBinaryResponse() {
        return false;
    }

    /**
     * Creates a response object from binary response data.
     *
     * @param responseBytes The raw binary response data
     * @return A new response object of type R
     * @throws UnsupportedOperationException if binary responses are not supported
     * @implNote Override this method to handle binary responses. The default implementation
     *           throws UnsupportedOperationException.
     */
    public R createResponse(byte[] responseBytes) {
        throw new UnsupportedOperationException("This request doesn't support binary responses.");
    }

    /**
     * Gets the Content-Type header value for this request.
     *
     * @return The content type (defaults to "application/json")
     * @implNote Override this method to specify a different content type
     *           (e.g., "application/x-www-form-urlencoded" or "multipart/form-data").
     */
    public String getContentType() {
        return "application/json";
    }

    /**
     * Gets the request body as binary data.
     *
     * @return The request body as a byte array
     * @throws UnsupportedOperationException if binary request bodies are not supported
     * @implNote Override this method to provide binary request data. The default
     *           implementation throws UnsupportedOperationException.
     */
    public byte[] getBodyBytes() {
        throw new UnsupportedOperationException("No binary body by default.");
    }

    /**
     * Adds or replaces a request header.
     *
     * @param name The header name (case-insensitive)
     * @param value The header value (null values are not allowed)
     * @throws NullPointerException if name or value is null
     * @throws IllegalArgumentException if name is empty or contains invalid characters
     */
    public void setHeader(String name, String value) {
        additionalHeaders.put(name, value);
    }

    /**
     * Gets an unmodifiable view of all additional headers.
     *
     * @return An unmodifiable map of header names to values
     * @see #setHeader(String, String)
     */
    public Map<String, String> getAdditionalHeaders() {
        return Collections.unmodifiableMap(additionalHeaders);
    }
}

package de.entwicklertraining.api.base;

import de.entwicklertraining.api.base.streaming.StreamingInfo;
import de.entwicklertraining.cancellation.CancellationToken;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
 *   <li>Optional streaming configuration</li>
 * </ul>
 *
 * @param <R> The type of ApiResponse this request will return
 * @since 2.0.0
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

    /** The cancellation token if one was provided (optional) */
    private final CancellationToken cancellationToken;
    
    /** Streaming configuration for this request (optional) */
    private final StreamingInfo streamingInfo;

    /**
     * Constructs a new ApiRequest using the provided builder.
     * <p>
     * This constructor is protected and should only be called by subclasses.
     * It initializes the request with settings from the builder.
     *
     * @param builderBase The builder containing the request configuration
     */
    protected ApiRequest(ApiRequestBuilderBase<?, ?> builderBase) {
        this.maxExecutionTimeInSeconds = builderBase.maxExecutionTimeInSeconds;
        this.captureOnSuccess = builderBase.captureOnSuccess;
        this.captureOnError = builderBase.captureOnError;
        this.cancellationToken = builderBase.cancellationToken;
        this.streamingInfo = builderBase.getStreamingInfo();
        // Combine external cancellation supplier with internal cancel flag
        this.isCanceledSupplier = builderBase.isCanceledSupplier != null 
            ? () -> this.canceled || builderBase.isCanceledSupplier.get()
            : () -> this.canceled;
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
     * Gets the cancellation token associated with this request, if any.
     * 
     * @return Optional containing the cancellation token, or empty if none was set
     */
    public Optional<CancellationToken> getCancellationToken() {
        return Optional.ofNullable(cancellationToken);
    }

    /**
     * Gets the relative URL path for this API request.
     * This should include the path and any query parameters, but not the base URL.
     *
     * @return The relative URL path (e.g., "/api/resource?param=value")
     * <p>
     * Implementation note: Must be implemented by subclasses to provide the endpoint path
     */
    public abstract String getRelativeUrl();

    /**
     * Gets the HTTP method for this request.
     *
     * @return The HTTP method as a string (e.g., "GET", "POST", "PUT", "DELETE")
     * <p>
     * Implementation note: Must be implemented by subclasses to specify the HTTP method
     */
    public abstract String getHttpMethod();

    /**
     * Gets the request body content.
     *
     * @return The request body as a string (e.g., JSON), or null for requests without a body
     * <p>
     * Implementation note: Must be implemented by subclasses for requests that include a body.
     * For binary content, override {@link #getBodyBytes()} instead.
     */
    public abstract String getBody();

    /**
     * Creates a response object from the given response body.
     *
     * @param responseBody The raw response body as received from the server
     * @return A new response object of type R
     * @throws RuntimeException if the response cannot be parsed or is invalid
     * <p>
     * Implementation note: Must be implemented by subclasses to parse the response body
     * and return an appropriate response object. Subclasses should throw specific runtime
     * exceptions to indicate different types of parsing or validation errors.
     */
    public abstract R createResponse(String responseBody);

    /**
     * Indicates whether this request expects a binary response.
     *
     * @return true if the response should be treated as binary data, false for text
     * <p>
     * Note: The default implementation returns false. Override this method
     * to return true for binary responses (e.g., file downloads).
     */
    public boolean isBinaryResponse() {
        return false;
    }
    
    /**
     * Gets the streaming configuration for this request.
     * 
     * @return The streaming info for this request
     * @since 2.1.0
     */
    public StreamingInfo getStreamingInfo() {
        return streamingInfo;
    }
    
    /**
     * Checks if streaming is enabled for this request.
     * 
     * @return true if this request should be processed as a stream
     * @since 2.1.0
     */
    public boolean isStreamingEnabled() {
        return streamingInfo != null && streamingInfo.isEnabled();
    }

    /**
     * Creates a response object from binary response data.
     *
     * @param responseBytes The raw binary response data
     * @return A new response object of type R
     * @throws UnsupportedOperationException if binary responses are not supported
     * <p>
     * Note: Override this method to handle binary responses. The default implementation
     * throws UnsupportedOperationException.
     */
    public R createResponse(byte[] responseBytes) {
        throw new UnsupportedOperationException("This request doesn't support binary responses.");
    }

    /**
     * Gets the Content-Type header value for this request.
     *
     * @return The content type (defaults to "application/json")
     * <p>
     * Note: Override this method to specify a different content type
     * (e.g., "application/x-www-form-urlencoded" or "multipart/form-data").
     */
    public String getContentType() {
        return "application/json";
    }

    /**
     * Gets the request body as binary data.
     *
     * @return The request body as a byte array
     * @throws UnsupportedOperationException if binary request bodies are not supported
     * <p>
     * Note: Override this method to provide binary request data. The default
     * implementation throws UnsupportedOperationException.
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
    
    /**
     * Cancels this API request.
     * 
     * <p>This method sets the internal canceled flag to true, which can be checked
     * by the API client to abort ongoing operations. The cancellation is cooperative -
     * it's up to the API client implementation to regularly check the cancellation status
     * and abort the operation accordingly.
     * 
     * <p>Once a request is canceled, subsequent calls to {@link #getIsCanceledSupplier()}
     * will return a supplier that evaluates to true.
     * 
     * <p>Note that canceling a request does not guarantee immediate termination of 
     * ongoing network operations, but provides a signal that the operation should
     * be aborted when feasible.
     */
    public void cancel() {
        this.canceled = true;
    }
}

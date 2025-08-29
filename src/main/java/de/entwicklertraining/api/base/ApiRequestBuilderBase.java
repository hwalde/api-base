package de.entwicklertraining.api.base;

import de.entwicklertraining.api.base.streaming.StreamingFormat;
import de.entwicklertraining.api.base.streaming.StreamingInfo;
import de.entwicklertraining.api.base.streaming.StreamingResponseHandler;
import de.entwicklertraining.api.base.streaming.StreamingConfig;
import de.entwicklertraining.cancellation.CancellationToken;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Abstract base class for all request builders that create ApiRequest instances.
 * <p>
 * This class provides common functionality for building API requests, including:
 * <ul>
 *   <li>Setting execution timeouts</li>
 *   <li>Configuring success/error capture callbacks</li>
 *   <li>Support for request cancellation</li>
 *   <li>Optional streaming configuration</li>
 *   <li>Default execute methods when ApiClient is provided</li>
 * </ul>
 *
 * <p>Subclasses should implement the builder pattern for their specific request types,
 * while this base class handles common request properties and building logic.
 *
 * @param <B> The concrete builder type (for method chaining)
 * @param <R> The concrete ApiRequest type this builder creates
 *
 * @see ApiRequest
 * @since 2.0.0
 */
public abstract class ApiRequestBuilderBase<B extends ApiRequestBuilderBase<B, R>, R extends ApiRequest<?>> {

    /** The API client for executing requests (optional) */
    private final ApiClient apiClient;

    /** Maximum execution time in seconds (0 for no timeout) */
    protected int maxExecutionTimeInSeconds = 0;

    /** Callback for capturing successful request data (optional) */
    protected Consumer<ApiCallCaptureInput> captureOnSuccess;

    /** Callback for capturing error data (optional) */
    protected Consumer<ApiCallCaptureInput> captureOnError;

    /** Supplier to check if the request has been canceled */
    protected Supplier<Boolean> isCanceledSupplier = () -> false;

    /** The cancellation token if one was provided (optional) */
    protected CancellationToken cancellationToken;
    
    /** Streaming configuration (optional) */
    protected StreamingInfo streamingInfo = new StreamingInfo();

    /**
     * Creates a new ApiRequestBuilderBase instance without ApiClient support.
     * Builders created with this constructor cannot use the execute methods directly.
     */
    public ApiRequestBuilderBase() {
        this.apiClient = null;
    }
    
    /**
     * Creates a new ApiRequestBuilderBase instance with ApiClient support.
     * Builders created with this constructor can use the execute methods directly.
     * 
     * @param apiClient The API client for executing requests
     * @throws NullPointerException if apiClient is null
     * @since 2.1.0
     */
    protected ApiRequestBuilderBase(ApiClient apiClient) {
        this.apiClient = Objects.requireNonNull(apiClient, "ApiClient cannot be null");
    }

    /**
     * Sets the maximum execution time for the request.
     *
     * @param seconds Maximum execution time in seconds (0 for no timeout)
     * @return This builder instance for method chaining
     */
    @SuppressWarnings("unchecked")
    public B maxExecutionTimeInSeconds(int seconds) {
        this.maxExecutionTimeInSeconds = seconds;
        return (B) this;
    }

    /**
     * Enables capturing of request and response data when a request succeeds.
     * <p>
     * The provided consumer will be called with an {@link ApiCallCaptureInput} object
     * containing details about the successful request and its response.
     *
     * @param onSuccessConsumer Consumer that will receive the captured success data
     * @return This builder instance for method chaining
     * @see ApiCallCaptureInput
     */
    @SuppressWarnings("unchecked")
    public B captureOnSuccess(Consumer<ApiCallCaptureInput> onSuccessConsumer) {
        this.captureOnSuccess = onSuccessConsumer;
        return (B) this;
    }

    /**
     * Enables capturing of error data when a request fails.
     *
     * @param onErrorConsumer Consumer that will receive the captured error data
     * @return This builder instance for method chaining
     */
    @SuppressWarnings("unchecked")
    public B captureOnError(Consumer<ApiCallCaptureInput> onErrorConsumer) {
        this.captureOnError = onErrorConsumer;
        return (B) this;
    }

    /**
     * Sets a supplier that can cancel the request.
     * The supplier should return true if the request should be canceled.
     *
     * @param isCanceledSupplier The cancel supplier
     * @return This builder instance for method chaining
     */
    @SuppressWarnings("unchecked")
    public B setCancelSupplier(Supplier<Boolean> isCanceledSupplier) {
        this.isCanceledSupplier = isCanceledSupplier;
        return (B) this;
    }

    /**
     * Sets a cancellation token that can cancel the request.
     * This provides a more standardized way to handle cancellation across different libraries.
     *
     * @param cancellationToken The cancellation token
     * @return This builder instance for method chaining
     * @throws IllegalArgumentException if cancellationToken is null
     */
    @SuppressWarnings("unchecked")
    public B setCancelToken(CancellationToken cancellationToken) {
        if (cancellationToken == null) {
            throw new IllegalArgumentException("Cancellation token cannot be null");
        }
        this.cancellationToken = cancellationToken;
        this.isCanceledSupplier = cancellationToken.asSupplier();
        return (B) this;
    }

    /**
     * Sets a CompletableFuture that can cancel the request when the future is cancelled.
     * This is useful for integration with asynchronous operations.
     *
     * @param future The CompletableFuture to monitor for cancellation
     * @return This builder instance for method chaining
     * @throws IllegalArgumentException if future is null
     */
    @SuppressWarnings("unchecked")
    public B setCancelFuture(CompletableFuture<?> future) {
        if (future == null) {
            throw new IllegalArgumentException("Future cannot be null");
        }
        this.isCanceledSupplier = future::isCancelled;
        return (B) this;
    }

    /**
     * Configures this request for streaming with the specified handler.
     * 
     * @param handler The handler to process streaming responses
     * @return This builder instance for method chaining
     * @since 2.1.0
     */
    @SuppressWarnings("unchecked")
    public B stream(StreamingResponseHandler<?> handler) {
        return stream(StreamingFormat.SERVER_SENT_EVENTS, handler);
    }
    
    /**
     * Configures this request for streaming with the specified format and handler.
     * 
     * @param format The streaming format to use
     * @param handler The handler to process streaming responses
     * @return This builder instance for method chaining
     * @since 2.1.0
     */
    @SuppressWarnings("unchecked")
    public B stream(StreamingFormat format, StreamingResponseHandler<?> handler) {
        this.streamingInfo = new StreamingInfo(format, handler);
        return (B) this;
    }
    
    /**
     * Configures this request for streaming with custom configuration.
     * 
     * @param format The streaming format to use
     * @param handler The handler to process streaming responses
     * @param config Custom streaming configuration
     * @return This builder instance for method chaining
     * @since 2.1.0
     */
    @SuppressWarnings("unchecked")
    public B streamWithConfig(StreamingFormat format, StreamingResponseHandler<?> handler, StreamingConfig config) {
        this.streamingInfo = new StreamingInfo(format, handler, config);
        return (B) this;
    }
    
    /**
     * Gets the streaming configuration for this builder.
     * 
     * @return The streaming info
     * @since 2.1.0
     */
    protected StreamingInfo getStreamingInfo() {
        return streamingInfo;
    }

    /**
     * Builds and returns the configured request instance.
     * <p>
     * This method should be implemented by subclasses to create and configure
     * the specific request type, then call the parent's build method to handle
     * common properties.
     *
     * @return A fully configured request instance
     */
    public abstract R build();

    /**
     * Builds and executes the request without automatic retries.
     *
     * @return The API response
     * @throws IllegalStateException if this builder was not initialized with an ApiClient
     * @throws ApiClient.ApiClientException if the request fails
     * @since 2.1.0
     */
    @SuppressWarnings("unchecked")
    public ApiResponse<R> execute() {
        requireApiClient();
        return (ApiResponse<R>) apiClient.execute(build());
    }

    /**
     * Builds and executes the request with automatic retry logic.
     *
     * @return The API response  
     * @throws IllegalStateException if this builder was not initialized with an ApiClient
     * @throws ApiClient.ApiClientException if the request fails after all retries
     * @since 2.1.0
     */
    @SuppressWarnings("unchecked")
    public ApiResponse<R> executeWithRetry() {
        requireApiClient();
        return (ApiResponse<R>) apiClient.executeWithRetry(build());
    }
    
    /**
     * Builds and executes the request asynchronously without automatic retries.
     *
     * @return A CompletableFuture containing the API response
     * @throws IllegalStateException if this builder was not initialized with an ApiClient
     * @since 2.1.0
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<ApiResponse<R>> executeAsync() {
        requireApiClient();
        return (CompletableFuture<ApiResponse<R>>) apiClient.executeAsync(build());
    }
    
    /**
     * Builds and executes the request asynchronously with automatic retry logic.
     *
     * @return A CompletableFuture containing the API response
     * @throws IllegalStateException if this builder was not initialized with an ApiClient
     * @since 2.1.0
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<ApiResponse<R>> executeAsyncWithRetry() {
        requireApiClient();
        return (CompletableFuture<ApiResponse<R>>) apiClient.executeAsyncWithRetry(build());
    }
    
    /**
     * @deprecated Use {@link #executeWithRetry()} instead
     * @return The API response
     */
    @Deprecated(since = "2.1.0", forRemoval = false)
    public ApiResponse<R> executeWithExponentialBackoff() {
        return executeWithRetry();
    }
    
    /**
     * Ensures that an ApiClient is available for execute operations.
     * 
     * @throws IllegalStateException if no ApiClient is available
     */
    private void requireApiClient() {
        if (apiClient == null) {
            throw new IllegalStateException(
                "This builder was not initialized with an ApiClient. " +
                "Use build() and call execute methods on the client directly, " +
                "or initialize the builder with an ApiClient instance."
            );
        }
    }

}

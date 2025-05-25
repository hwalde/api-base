package de.entwicklertraining.api.base;

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
 * </ul>
 *
 * <p>Subclasses should implement the builder pattern for their specific request types,
 * while this base class handles common request properties and building logic.
 *
 * @param <B> The concrete builder type (for method chaining)
 * @param <R> The concrete ApiRequest type this builder creates
 *
 * @see ApiRequest
 */
public abstract class ApiRequestBuilderBase<B extends ApiRequestBuilderBase<B, R>, R extends ApiRequest<?>> {

    /** Maximum execution time in seconds (0 for no timeout) */
    protected int maxExecutionTimeInSeconds = 0;

    /** Callback for capturing successful request data (optional) */
    protected Consumer<ApiCallCaptureInput> captureOnSuccess;

    /** Callback for capturing error data (optional) */
    protected Consumer<ApiCallCaptureInput> captureOnError;

    /** Supplier to check if the request has been canceled */
    protected Supplier<Boolean> isCanceledSupplier = () -> false;

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
     * Aktiviert das Datenspeichern im Erfolgsfall (kein Exception-Abbruch).
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
     * Builds and executes the request with automatic retry logic.
     *
     * @return The API response
     * @throws ApiClientException if the request fails after all retries
     * @see ApiClient#sendRequest(ApiRequest)
     */
    public abstract ApiResponse<R> execute();

    /**
     * Builds and executes the request without automatic retries.
     *
     * @return The API response
     * @throws ApiClientException if the request fails
     * @see ApiClient#sendRequestWithoutExponentialBackoff(ApiRequest)
     */
    public abstract ApiResponse<R> executeWithoutExponentialBackoff();

}

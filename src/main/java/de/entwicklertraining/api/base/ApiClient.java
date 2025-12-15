package de.entwicklertraining.api.base;

import de.entwicklertraining.api.base.streaming.*;
import de.entwicklertraining.cancellation.CancellationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.Optional;
import java.util.stream.Stream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Abstract base class for a generic API client with
 * standard HTTP requests and exponential backoff retry mechanism.
 *
 * <p>This class does not contain specific logic for a particular service.
 * Concrete clients (e.g., "GeminiClient") inherit and can
 * use 'registerStatusCodeException' for error handling.
 *
 * <p>Subclasses should call {@link #setBaseUrl(String)} in their constructor
 * to set the base URL for all API requests and register
 * status code with {@link #registerStatusCodeException(int, Class, String, boolean)}.
 */
public abstract class ApiClient {
    private static final Logger logger = LoggerFactory.getLogger(ApiClient.class.getName());

    // Nun Virtual Threads für den HttpClient
    private static final ExecutorService HTTP_CLIENT_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    // Auch der Cancel-Watcher läuft in virtuellen Threads
    private static final ExecutorService CANCEL_WATCHER_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    /** The HTTP client used to execute requests */
    protected final HttpClient httpClient;

    /** The settings for this API client */
    protected ApiClientSettings settings;
    
    /** The HTTP configuration for this API client */
    protected ApiHttpConfiguration httpConfig;
    private Optional<String> baseUrl = Optional.empty();
    private boolean statusCodeExceptionsWarningLogged = false;

    /**
     * Internal class for registering custom exceptions for specific HTTP status codes.
     */
    /**
     * Internal class for registering custom exceptions for specific HTTP status codes.
     */
    protected static final class StatusCodeExceptionRegistration {
        final Class<? extends RuntimeException> exceptionClass;
        final String message;
        final boolean retry;

        StatusCodeExceptionRegistration(
                Class<? extends RuntimeException> exceptionClass,
                String message,
                boolean retry
        ) {
            this.exceptionClass = exceptionClass;
            this.message = message;
            this.retry = retry;
        }
    }

    private final Map<Integer, StatusCodeExceptionRegistration> statusCodeExceptions = new HashMap<>();

    /**
     * Gets the base URL for all API requests (e.g., "https://api.example.com").
     * This should include the protocol (http/https) and domain, but no trailing slash.
     *
     * @return The base URL as a string
     * @throws IllegalStateException if the base URL has not been set
     */
    protected String getBaseUrl() {
        return baseUrl.orElseThrow(() -> new IllegalStateException(
            "Base URL has not been set. Call setBaseUrl() in the constructor of your client implementation."));
    }

    /**
     * Sets the base URL for all API requests.
     * This method should be called in the constructor of the implementing class.
     *
     * @param baseUrl The base URL (e.g., "https://api.example.com")
     * @throws IllegalArgumentException if the baseUrl is null or empty
     */
    protected final void setBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Base URL cannot be null or empty");
        }
        // Ensure the base URL doesn't end with a slash
        this.baseUrl = Optional.of(baseUrl.endsWith("/") ? 
            baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
    }

    /**
     * Creates a new ApiClient with the specified settings.
     *
     * @param settings The settings to use for this client
     * @throws NullPointerException if settings is null
     */
    protected ApiClient(ApiClientSettings settings) {
        this(settings, new ApiHttpConfiguration());
    }
    
    /**
     * Creates a new ApiClient with the specified settings and HTTP configuration.
     *
     * @param settings The settings to use for this client
     * @param httpConfig The HTTP configuration to use for this client
     * @throws NullPointerException if settings or httpConfig is null
     */
    protected ApiClient(ApiClientSettings settings, ApiHttpConfiguration httpConfig) {
        // HttpClient an Virtual-Thread-Executor binden:
        this.httpClient = HttpClient.newBuilder()
                .executor(HTTP_CLIENT_EXECUTOR)
                .build();
        this.settings = settings;
        this.httpConfig = httpConfig;
    }

    /**
     * Registers a custom exception type to be thrown when a specific HTTP status code is received.
     * This allows for type-safe error handling based on HTTP status codes.
     *
     * @param statusCode The HTTP status code to handle
     * @param exceptionClass The exception class to instantiate when this status code is received
     * @param message The error message to include in the exception
     * @param retry Whether requests that fail with this status code should be retried
     */
    protected final void registerStatusCodeException(
            int statusCode,
            Class<? extends RuntimeException> exceptionClass,
            String message,
            boolean retry
    ) {
        statusCodeExceptions.put(statusCode, new StatusCodeExceptionRegistration(exceptionClass, message, retry));
    }

    /**
     * Sends an API request with automatic retry logic using exponential backoff.
     * This method will automatically retry failed requests according to the configured
     * retry policy in ApiClientSettings.
     *
     * @param <T> The type of the API request that extends ApiRequest
     * @param <U> The type of the API response that extends ApiResponse
     * @param request The API request to send
     * @return The API response
     * @throws ApiTimeoutException if the request times out or maximum retries are exceeded
     * @throws ApiClientException if there is an error executing the request
     * @deprecated Use {@link #sendRequestWithRetry(ApiRequest)} instead for consistent naming with streaming API
     */
    @Deprecated(since = "1.1.0", forRemoval = false)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends ApiRequest<U>, U extends ApiResponse<T>> U sendRequestWithExponentialBackoff(T request) {
        return sendRequestWithRetry(request);
    }

    /**
     * Sends an API request with automatic retry logic using exponential backoff.
     * This method will automatically retry failed requests according to the configured
     * retry policy in ApiClientSettings.
     *
     * @param <T> The type of the API request that extends ApiRequest
     * @param <U> The type of the API response that extends ApiResponse
     * @param request The API request to send
     * @return The API response
     * @throws ApiTimeoutException if the request times out or maximum retries are exceeded
     * @throws ApiClientException if there is an error executing the request
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends ApiRequest<U>, U extends ApiResponse<T>> U sendRequestWithRetry(T request) {
        if(settings.getBeforeSendAction() != null) {
            settings.getBeforeSendAction().accept(request);
        }

        long startMillis = System.currentTimeMillis();
        Instant startInstant = Instant.ofEpochMilli(startMillis);

        boolean success = false;
        RuntimeException finalException = null;
        U finalResponse = null;

        // Create a context for capturing response data
        ApiRequestExecutionContext<T, U> context = new ApiRequestExecutionContext<>();

        try {
            finalResponse = executeRequestWithRetry(() -> runRequest(request, context), request);
            success = true;
            return finalResponse;
        } catch (RuntimeException ex) {
            finalException = ex;
            throw ex;
        } finally {
            // Nur am Ende (nach letztem Retry oder Erfolg) Daten speichern
            Instant endInstant = Instant.ofEpochMilli(System.currentTimeMillis());
            if (success && request.hasCaptureOnSuccess()) {
                storeCaptureData(request, request.getCaptureOnSuccess(), finalResponse, context, finalException, startInstant, endInstant, success);
            } else if (!success && request.hasCaptureOnError()) {
                storeCaptureData(request, request.getCaptureOnError(), finalResponse, context, finalException, startInstant, endInstant, success);
            }
        }
    }

    /**
     * Executes an API request without automatic retry logic.
     * This method automatically handles both regular and streaming requests based on the request configuration.
     *
     * @param <T> The type of the API request that extends ApiRequest
     * @param request The API request to execute
     * @return The API response as ApiResponse&lt;?&gt;
     * @throws ApiTimeoutException if the request times out
     * @throws ApiClientException if there is an error executing the request
     * @since 2.1.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends ApiRequest<?>> ApiResponse<?> execute(T request) {
        if (request.isStreamingEnabled()) {
            return executeStreamingAsync(request).join();
        }
        return sendRequest((ApiRequest) request);
    }
    
    /**
     * Executes an API request with automatic retry logic using exponential backoff.
     * This method automatically handles both regular and streaming requests based on the request configuration.
     *
     * @param <T> The type of the API request that extends ApiRequest
     * @param request The API request to execute
     * @return The API response as ApiResponse&lt;?&gt;
     * @throws ApiTimeoutException if the request times out or maximum retries are exceeded
     * @throws ApiClientException if there is an error executing the request
     * @since 2.1.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends ApiRequest<?>> ApiResponse<?> executeWithRetry(T request) {
        if (request.isStreamingEnabled()) {
            return executeStreamingAsyncWithRetry(request).join();
        }
        return sendRequestWithRetry((ApiRequest) request);
    }
    
    /**
     * Executes an API request asynchronously without automatic retry logic.
     * This method automatically handles both regular and streaming requests based on the request configuration.
     *
     * @param <T> The type of the API request that extends ApiRequest
     * @param request The API request to execute
     * @return A CompletableFuture containing the API response as CompletableFuture&lt;? extends ApiResponse&lt;?&gt;&gt;
     * @since 2.1.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends ApiRequest<?>> CompletableFuture<? extends ApiResponse<?>> executeAsync(T request) {
        if (request.isStreamingEnabled()) {
            return executeStreamingAsync(request);
        }
        return CompletableFuture.supplyAsync(() -> (ApiResponse<?>) sendRequest((ApiRequest) request), HTTP_CLIENT_EXECUTOR);
    }
    
    /**
     * Executes an API request asynchronously with automatic retry logic using exponential backoff.
     * This method automatically handles both regular and streaming requests based on the request configuration.
     *
     * @param <T> The type of the API request that extends ApiRequest
     * @param request The API request to execute
     * @return A CompletableFuture containing the API response as CompletableFuture&lt;? extends ApiResponse&lt;?&gt;&gt;
     * @since 2.1.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends ApiRequest<?>> CompletableFuture<? extends ApiResponse<?>> executeAsyncWithRetry(T request) {
        if (request.isStreamingEnabled()) {
            return executeStreamingAsyncWithRetry(request);
        }
        return CompletableFuture.supplyAsync(() -> (ApiResponse<?>) sendRequestWithRetry((ApiRequest) request), HTTP_CLIENT_EXECUTOR);
    }
    

    /**
     * Sends an API request without automatic retry logic.
     * This method will not retry failed requests, making it suitable for non-idempotent operations.
     * 
     * @deprecated Use {@link #execute(ApiRequest)} instead
     * @param <T> The type of the API request that extends ApiRequest
     * @param <U> The type of the API response that extends ApiResponse
     * @param request The API request to send
     * @return The API response
     * @throws ApiTimeoutException if the request times out
     * @throws ApiClientException if there is an error executing the request
     */
    @Deprecated(since = "2.1.0", forRemoval = false)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends ApiRequest<U>, U extends ApiResponse<T>> U sendRequest(T request) {
        if(settings.getBeforeSendAction() != null) {
            settings.getBeforeSendAction().accept(request);
        }

        long startMillis = System.currentTimeMillis();
        Instant startInstant = Instant.ofEpochMilli(startMillis);

        boolean success = false;
        RuntimeException finalException = null;
        U finalResponse = null;

        final int maxDurationSeconds = request.getMaxExecutionTimeInSeconds();
        final long maxDurationMs = maxDurationSeconds * 1000L;

        // Create a context for capturing response data
        ApiRequestExecutionContext<T, U> context = new ApiRequestExecutionContext<>();

        try {
            finalResponse = executeRequestWithTimeout(() -> runRequest(request, context), maxDurationMs);
            success = true;
            return finalResponse;
        } catch (RuntimeException ex) {
            finalException = ex;
            throw ex;
        } finally {
            Instant endInstant = Instant.ofEpochMilli(System.currentTimeMillis());
            if (success && request.hasCaptureOnSuccess()) {
                storeCaptureData(request, request.getCaptureOnSuccess(), finalResponse, context, finalException, startInstant, endInstant, success);
            } else if (!success && request.hasCaptureOnError()) {
                storeCaptureData(request, request.getCaptureOnError(), finalResponse, context, finalException, startInstant, endInstant, success);
            }
        }
    }

    /**
     * Executes a single HTTP request asynchronously using Java's HttpClient.
     * Handles request cancellation, timeouts, and response processing.
     *
     * @param <T> The type of the API request that extends ApiRequest
     * @param <U> The type of the API response that extends ApiResponse
     * @param request The API request to execute
     * @param context The execution context for storing request/response data
     * @return The API response
     * @throws ApiTimeoutException if the request is cancelled or times out
     * @throws ApiClientException if there is an error executing the request
     */
    protected <T extends ApiRequest<U>, U extends ApiResponse<T>> U runRequest(T request,
                                                                               ApiRequestExecutionContext<T, U> context) {
        // Check if any status code exceptions are registered
        if (statusCodeExceptions.isEmpty() && !statusCodeExceptionsWarningLogged) {
            logger.warn("No status code exceptions registered. It's recommended to register " +
                "appropriate status code exceptions in the constructor of your client implementation " +
                "using registerStatusCodeException().");
            statusCodeExceptionsWarningLogged = true;
        }

        String fullUrl = getBaseUrl() + request.getRelativeUrl();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header("Content-Type", request.getContentType());

        // Apply global headers from HTTP configuration
        httpConfig.getGlobalHeaders().forEach(builder::header);
        
        // Apply request modifiers from HTTP configuration
        httpConfig.getRequestModifiers().forEach(modifier -> modifier.accept(builder));

        // Apply request-specific headers (these can override global headers)
        for (Map.Entry<String, String> entry : request.getAdditionalHeaders().entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }

        String method = request.getHttpMethod().toUpperCase();
        switch (method) {
            case "POST" -> {
                if (request.getContentType().startsWith("multipart/form-data")) {
                    byte[] bodyBytes = request.getBodyBytes();
                    builder.POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
                } else {
                    builder.POST(HttpRequest.BodyPublishers.ofString(request.getBody()));
                }
            }
            case "GET" -> builder.GET();
            case "DELETE" -> builder.DELETE();
            default -> throw new ApiClientException("Unsupported HTTP method: " + method);
        }

        HttpRequest httpRequest = builder.build();

        // Asynchrones Senden über den HttpClient (nun virtueller Thread)
        CompletableFuture<HttpResponse<?>> future;
        if (request.isBinaryResponse()) {
            future = httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
                    .thenApply(response -> (HttpResponse<?>) response);
        } else {
            future = httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> (HttpResponse<?>) response);
        }

        // Neuer Thread, der alle 100ms checkt, ob request.cancel() aufgerufen wurde:
        CompletableFuture<Void> cancelWatcher = CompletableFuture.runAsync(() -> {
            try {
                while (true) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    if (future.isDone()) {
                        break;
                    }
                    if (request.getIsCanceledSupplier().get()) {
                        future.cancel(true);
                        break;
                    }
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, CANCEL_WATCHER_EXECUTOR);

        try {
            // Prüfung auf Thread-Unterbrechung
            if (Thread.currentThread().isInterrupted()) {
                future.cancel(true);
                throw new ApiTimeoutException("Thread was interrupted before sending request");
            }

            HttpResponse<?> httpResponse;
            if(request.getMaxExecutionTimeInSeconds() > 0) {
                httpResponse = future.get(request.getMaxExecutionTimeInSeconds(), TimeUnit.SECONDS);
            } else {
                httpResponse = future.get();
            }

            int statusCode = httpResponse.statusCode();
            if (statusCode == 200) {
                if (request.isBinaryResponse()) {
                    byte[] bodyBytes = (byte[]) httpResponse.body();
                    context.setResponseBytes(bodyBytes);
                    return request.createResponse(bodyBytes);
                } else {
                    String bodyString = (String) httpResponse.body();
                    context.setResponseBody(bodyString);
                    return request.createResponse(bodyString);
                }
            }

            // Fehlerstatuscode => Fehlertyp ermitteln
            String bodySnippet;
            if (request.isBinaryResponse()) {
                byte[] respBytes = (byte[]) httpResponse.body();
                context.setResponseBytes(respBytes);
                bodySnippet = new String(respBytes);
            } else {
                String respString = (String) httpResponse.body();
                context.setResponseBody(respString);
                bodySnippet = respString;
            }

            StatusCodeExceptionRegistration reg = statusCodeExceptions.get(statusCode);
            if (reg != null) {
                throw createException(reg, bodySnippet);
            }

            throw new ApiClientException("Unexpected HTTP status " + statusCode + " - " + bodySnippet);

        } catch (java.util.concurrent.CancellationException cex) {
            // CompletableFuture was canceled => graceful cancellation
            throw new CancellationException("Request was canceled", cex);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new ApiClientException("Request interrupted", ie);

        } catch (ExecutionException ex) {
            future.cancel(true);
            if (ex.getCause() != null) {
                throw new ApiClientException("Request failed: " + ex.getCause().getMessage(), ex.getCause());
            }
            throw new ApiClientException("Execution failed", ex);

        } catch (TimeoutException e) {
            throw new ApiTimeoutException("Maximum execution timeout of " + request.getMaxExecutionTimeInSeconds() + " seconds has been reached!", e);

        } finally {
            // cancelWatcher beenden
            cancelWatcher.cancel(true);
        }
    }

    private RuntimeException createException(StatusCodeExceptionRegistration reg, String bodySnippet) {
        String errorMsg = reg.message + ": " + bodySnippet;
        try {
            return reg.exceptionClass.getConstructor(String.class).newInstance(errorMsg);
        } catch (NoSuchMethodException
                 | InstantiationException
                 | IllegalAccessException
                 | InvocationTargetException e) {
            return new ApiClientException(errorMsg, e);
        }
    }

    /**
     * Executes an operation with retry logic using exponential backoff.
     * The retry behavior is controlled by the ApiClientSettings configuration.
     *
     * @param <U> The type of the operation result that extends ApiResponse
     * @param operation The operation to execute
     * @param request The API request being processed
     * @return The result of the operation
     * @throws ApiTimeoutException if maximum retries are exceeded or the operation times out
     */
    protected <U extends ApiResponse<?>> U executeRequestWithRetry(Supplier<U> operation, ApiRequest<?> request) {
        final long startTimeMs = System.currentTimeMillis();
        final int maxDurationSeconds = request.getMaxExecutionTimeInSeconds();
        final long maxDurationMs = maxDurationSeconds * 1000L;

        String latestReason = null;
        Throwable latestException = null;

        for (int attempt = 1; attempt <= settings.getMaxRetries(); attempt++) {
            // Check for cancellation before each retry attempt
            if (request.getIsCanceledSupplier().get()) {
                throw new CancellationException("Request was cancelled before retry attempt " + attempt);
            }
            
            try {
                long elapsedMs = System.currentTimeMillis() - startTimeMs;
                long remainingMs = maxDurationMs - elapsedMs;

                if (maxDurationMs > 0 && remainingMs <= 0) {
                    throw createTimeoutException(latestReason, maxDurationSeconds, latestException);
                }

                return executeRequestWithTimeout(operation, remainingMs);

            } catch (Throwable ex) {
                if (isRetryableException(ex)) {
                    latestReason = "Retriable error: " + ex.getMessage();
                    latestException = ex;
                } else {
                    throw ex;
                }
            }

            if (attempt == settings.getMaxRetries()) {
                throw createRetriesExhaustedException(latestReason, latestException);
            }

            long elapsedMs = System.currentTimeMillis() - startTimeMs;
            long remainingMs = maxDurationMs - elapsedMs;
            if (maxDurationMs > 0 && remainingMs <= 0) {
                throw createTimeoutException(latestReason, maxDurationSeconds, latestException);
            }

            // Check for cancellation before sleeping
            if (request.getIsCanceledSupplier().get()) {
                throw new CancellationException("Request was cancelled before retry sleep");
            }

            long durationOfNextSleep = calculateNextSleep(settings.getInitialDelayMs());
            durationOfNextSleep = maybeAdjustSleepForFinalRetry(maxDurationSeconds, durationOfNextSleep, remainingMs);
            applySleep(durationOfNextSleep, remainingMs);
        }

        throw new ApiClientException("Exponential backoff logic exhausted unexpectedly.");
    }

    private boolean isRetryableException(Throwable ex) {
        Class<? extends Throwable> exClass = ex.getClass();
        for (StatusCodeExceptionRegistration reg : statusCodeExceptions.values()) {
            if (reg.exceptionClass.isAssignableFrom(exClass) && reg.retry) {
                return true;
            }
        }
        return false;
    }

    /**
     * Executes an operation with a timeout.
     *
     * @param <U> The type of the operation result
     * @param operation The operation to execute
     * @param remainingMs The maximum time to wait for the operation to complete, in milliseconds
     * @return The result of the operation
     * @throws ApiTimeoutException if the operation times out
     * @throws ApiClientException if the operation is interrupted or fails
     */
    protected <U> U executeRequestWithTimeout(Supplier<U> operation, long remainingMs) {
        final CompletableFuture<U> future = CompletableFuture.supplyAsync(operation);

        try {
            if (remainingMs > 0) {
                return future.get(remainingMs, TimeUnit.MILLISECONDS);
            }
            return future.get();
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new ApiTimeoutException("Request timed out during execution", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new ApiClientException("Request interrupted", ex); // maybe needs to be removed
        } catch (ExecutionException ex) {
            future.cancel(true);
            if (ex.getCause() != null) {
                throw (RuntimeException) ex.getCause();
            }
            throw new ApiClientException("Execution failed", ex);
        }
    }

    /**
     * Calculates the next sleep duration using exponential backoff.
     *
     * @param currentDelay The current delay in milliseconds
     * @return The next delay in milliseconds
     */
    protected long calculateNextSleep(long currentDelay) {
        double factor = settings.getExponentialBase();
        if (settings.isUseJitter()) {
            factor *= (1.0 + Math.random());
        }
        return (long) (currentDelay * factor);
    }

    /**
     * Adjusts the sleep duration for the final retry attempt to respect the maximum execution time.
     *
     * @param maxDurationSeconds Maximum allowed duration in seconds
     * @param delayMs The calculated delay in milliseconds
     * @param remainingMs Remaining time in milliseconds
     * @return The adjusted sleep duration in milliseconds
     */
    protected long maybeAdjustSleepForFinalRetry(int maxDurationSeconds, long delayMs, long remainingMs) {
        if (maxDurationSeconds == 0) { // feature is only relevant if there is a maximum execution time
            return delayMs;
        }
        boolean isTheNextSleepPhaseLongerThanTheRemainingTime = delayMs > remainingMs;

        long minSleepMs = TimeUnit.SECONDS.toMillis(settings.getMinSleepDurationForFinalRetryInSeconds());
        boolean isTheRemainingTimeLongEnoughToApplyAFinalRetry = minSleepMs >= remainingMs;

        if (isTheNextSleepPhaseLongerThanTheRemainingTime && isTheRemainingTimeLongEnoughToApplyAFinalRetry) {
            long cutoffExpectedExecutionTimeForFinalRetry = TimeUnit.SECONDS.toMillis(settings.getMaxExecutionTimeForFinalRetryInSeconds());
            return remainingMs - cutoffExpectedExecutionTimeForFinalRetry;
        }
        return delayMs;
    }

    /**
     * Sleeps for 'delayMs', in case there is enough time left.
     * @param delayMs The time to sleep, in milliseconds
     * @param remainingMs The time remaining before the request times out, in milliseconds
     */
    protected void applySleep(long delayMs, long remainingMs) {
        if (remainingMs > 0 && delayMs > remainingMs) {
            throw new ApiTimeoutException(
                    "Skipping sleep to prevent timeout (remaining: "
                            + TimeUnit.MILLISECONDS.toSeconds(remainingMs) + "s)"
            );
        }

        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiClientException("Interrupted during backoff", e);
        }
    }

    /**
     * Creates a new ApiTimeoutException with details about the timeout.
     *
     * @param reason The reason for the timeout
     * @param maxDurationSeconds The maximum duration that was allowed
     * @param cause The cause of the timeout
     * @return A new ApiTimeoutException instance
     */
    protected ApiTimeoutException createTimeoutException(
            String reason,
            int maxDurationSeconds,
            Throwable cause
    ) {
        String msg = "Maximum execution time of " + maxDurationSeconds + "s reached!";
        if (reason != null) msg += " " + reason;
        return new ApiTimeoutException(msg, cause);
    }

    /**
     * Creates a new ApiTimeoutException indicating that all retry attempts were exhausted.
     *
     * @param reason The reason for the failure
     * @param cause The cause of the failure
     * @return A new ApiTimeoutException instance
     */
    protected ApiTimeoutException createRetriesExhaustedException(
            String reason,
            Throwable cause
    ) {
        String msg = "Maximum retries of " + settings.getMaxRetries() + " exhausted!";
        if (reason != null) msg += " " + reason;
        return new ApiTimeoutException(msg, cause);
    }

    // ---------------------------------------
    // Interne Exceptions
    // ---------------------------------------

    /**
     * Exception thrown when the API returns HTTP 429 (Too Many Requests).
     * <p>
     * This status code indicates that the user has sent too many requests in a given amount of time.
     * It can be due to either rate limiting (too many requests per time window) or quota limits
     * (exceeding the allowed number of requests or data volume).
     */
    public static class HTTP_429_RateLimitOrQuotaException extends RuntimeException {
        /**
         * Type of rate limit that was exceeded.
         */
        public enum ExceptionType {
            /** The type of rate limiting is unknown */
            Unknown,
            /** Rate limit was exceeded (too many requests per time window) */
            RateLimit,
            /** Quota limit was exceeded (overall usage limit) */
            Quota
        }

        /** The type of rate limit that was exceeded */
        private ExceptionType type;

        /**
         * Creates a new HTTP 429 Too Many Requests exception.
         *
         * @param message The detail message
         */
        public HTTP_429_RateLimitOrQuotaException(String message) {
            super(message);
            this.type = ExceptionType.Unknown;
        }

        /**
         * Creates a new HTTP 429 Too Many Requests exception with the specified cause.
         *
         * @param message The detail message
         * @param cause The cause of the exception
         */
        public HTTP_429_RateLimitOrQuotaException(String message, Throwable cause) {
            super(message, cause);
            this.type = ExceptionType.RateLimit;
        }

        /**
         * Sets the type of rate limit that was exceeded.
         *
         * @param type The type of rate limit that was exceeded
         */
        public void setType(ExceptionType type) {
            this.type = type;
        }

        /**
         * Gets the type of rate limit that was exceeded.
         *
         * @return The type of rate limit that was exceeded
         */
        public ExceptionType getType() {
            return type;
        }
    }

    /**
     * Exception thrown when the API returns HTTP 400 (Bad Request).
     * <p>
     * This status code indicates that the server cannot or will not process the request
     * due to something that is perceived to be a client error (e.g., malformed request syntax,
     * invalid request message framing, or deceptive request routing).
     */
    public static class HTTP_400_RequestRejectedException extends RuntimeException {
        /**
         * Creates a new HTTP 400 Bad Request exception.
         *
         * @param message The detail message
         */
        public HTTP_400_RequestRejectedException(String message) {
            super(message);
        }

        /**
         * Creates a new HTTP 400 Bad Request exception with the specified cause.
         *
         * @param message The detail message
         * @param cause The cause of the exception
         */
        public HTTP_400_RequestRejectedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when the API returns HTTP 401 (Unauthorized).
     * <p>
     * This status code indicates that the request has not been applied because it lacks valid
     * authentication credentials for the target resource. The server generating a 401 response
     * MUST send a WWW-Authenticate header field containing at least one challenge.
     */
    public static class HTTP_401_AuthorizationException extends RuntimeException {
        /**
         * Creates a new HTTP 401 Unauthorized exception.
         *
         * @param message The detail message
         */
        public HTTP_401_AuthorizationException(String message) {
            super(message);
        }

        /**
         * Creates a new HTTP 401 Unauthorized exception with the specified cause.
         *
         * @param message The detail message
         * @param cause The cause of the exception
         */
        public HTTP_401_AuthorizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when the API returns HTTP 402 (Payment Required).
     * <p>
     * This status code is reserved for future use. The original intention was that it might be
     * used as part of some form of digital cash or micropayment scheme, but no standard
     * convention exists. Some APIs use this status code to indicate that the account has
     * insufficient funds or requires a subscription upgrade.
     */
    public static class HTTP_402_PaymentRequiredException extends RuntimeException {
        /**
         * Creates a new HTTP 402 Payment Required exception.
         *
         * @param message The detail message
         */
        public HTTP_402_PaymentRequiredException(String message) {
            super(message);
        }

        /**
         * Creates a new HTTP 402 Payment Required exception with the specified cause.
         *
         * @param message The detail message
         * @param cause The cause of the exception
         */
        public HTTP_402_PaymentRequiredException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when the API returns HTTP 403 (Forbidden).
     * <p>
     * This status code indicates that the server understood the request but refuses to
     * authorize it. A server that wishes to make public why the request has been forbidden
     * can describe that reason in the response payload (if any). Unlike 401 Unauthorized,
     * the client's identity is known to the server but the authenticated user doesn't have
     * permission to access the requested resource.
     */
    public static class HTTP_403_PermissionDeniedException extends RuntimeException {
        /**
         * Creates a new HTTP 403 Forbidden exception.
         *
         * @param message The detail message
         */
        public HTTP_403_PermissionDeniedException(String message) {
            super(message);
        }

        /**
         * Creates a new HTTP 403 Forbidden exception with the specified cause.
         *
         * @param message The detail message
         * @param cause The cause of the exception
         */
        public HTTP_403_PermissionDeniedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when the API returns HTTP 404 (Not Found).
     * <p>
     * This status code indicates that the origin server did not find a current representation
     * for the target resource or is not willing to disclose that one exists. A 404 response
     * is cacheable by default; i.e., unless otherwise indicated by the method definition or
     * explicit cache controls.
     */
    public static class HTTP_404_NotFoundException extends RuntimeException {
        /**
         * Creates a new HTTP 404 Not Found exception.
         *
         * @param message The detail message
         */
        public HTTP_404_NotFoundException(String message) {
            super(message);
        }

        /**
         * Creates a new HTTP 404 Not Found exception with the specified cause.
         *
         * @param message The detail message
         * @param cause The cause of the exception
         */
        public HTTP_404_NotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when the API returns HTTP 422 (Unprocessable Entity).
     * <p>
     * This status code indicates that the server understands the content type of the
     * request entity, and the syntax of the request entity is correct, but it was
     * unable to process the contained instructions. This is commonly used for validation
     * errors where the request is well-formed but contains semantic errors.
     */
    public static class HTTP_422_UnprocessableEntityException extends RuntimeException {
        /**
         * Creates a new HTTP 422 Unprocessable Entity exception.
         *
         * @param message The detail message
         */
        public HTTP_422_UnprocessableEntityException(String message) {
            super(message);
        }

        /**
         * Creates a new HTTP 422 Unprocessable Entity exception with the specified cause.
         *
         * @param message The detail message
         * @param cause The cause of the exception
         */
        public HTTP_422_UnprocessableEntityException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when the API returns HTTP 500 (Internal Server Error).
     * <p>
     * This status code indicates that the server encountered an unexpected condition
     * that prevented it from fulfilling the request. This is a generic "catch-all"
     * response when the server encounters an error that doesn't fit other status codes.
     */
    public static class HTTP_500_ServerErrorException extends RuntimeException {
        /**
         * Creates a new HTTP 500 Internal Server Error exception.
         *
         * @param message The detail message
         */
        public HTTP_500_ServerErrorException(String message) {
            super(message);
        }

        /**
         * Creates a new HTTP 500 Internal Server Error exception with the specified cause.
         *
         * @param message The detail message
         * @param cause The cause of the exception
         */
        public HTTP_500_ServerErrorException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when the API returns HTTP 503 (Service Unavailable).
     * <p>
     * This status code indicates that the server is currently unable to handle the request
     * due to a temporary overload or scheduled maintenance, which will likely be
     * alleviated after some delay. The server MAY send a Retry-After header field to
     * suggest an appropriate amount of time for the client to wait before retrying.
     */
    public static class HTTP_503_ServerUnavailableException extends RuntimeException {
        /**
         * Creates a new HTTP 503 Service Unavailable exception.
         *
         * @param message The detail message
         */
        public HTTP_503_ServerUnavailableException(String message) {
            super(message);
        }

        /**
         * Creates a new HTTP 503 Service Unavailable exception with the specified cause.
         *
         * @param message The detail message
         * @param cause The cause of the exception
         */
        public HTTP_503_ServerUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when the API returns HTTP 504 (Gateway Timeout).
     * <p>
     * This status code indicates that the server, while acting as a gateway or proxy,
     * did not receive a timely response from an upstream server it needed to access
     * in order to complete the request. This typically indicates a network connectivity
     * issue between servers rather than a problem with the client's request.
     */
    public static class HTTP_504_ServerTimeoutException extends RuntimeException {
        /**
         * Creates a new HTTP 504 Gateway Timeout exception.
         *
         * @param message The detail message
         */
        public HTTP_504_ServerTimeoutException(String message) {
            super(message);
        }

        /**
         * Creates a new HTTP 504 Gateway Timeout exception with the specified cause.
         *
         * @param message The detail message
         * @param cause The cause of the exception
         */
        public HTTP_504_ServerTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Base exception class for all client-side errors that occur during API operations.
     * <p>
     * This exception is used for errors that occur before a valid HTTP response is received,
     * or when the response cannot be properly processed. Common cases include:
     * <ul>
     *   <li>Network connectivity issues</li>
     *   <li>Request serialization/deserialization errors</li>
     *   <li>Interrupted operations</li>
     *   <li>Unexpected runtime errors during request processing</li>
     * </ul>
     *
     * <p>This is different from the HTTP_*Exception classes (like HTTP_404_NotFoundException)
     * which represent specific HTTP error responses from the server. This exception indicates
     * that the client encountered an issue before receiving a valid server response.
     */
    public static class ApiClientException extends RuntimeException {
        /**
         * Creates a new ApiClientException with the specified detail message.
         *
         * @param message the detail message (which is saved for later retrieval by the getMessage() method)
         */
        public ApiClientException(String message) {
            super(message);
        }

        /**
         * Creates a new ApiClientException with the specified detail message and cause.
         *
         * @param message the detail message (which is saved for later retrieval by the getMessage() method)
         * @param cause the cause (which is saved for later retrieval by the getCause() method)
         */
        public ApiClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception indicating that while the API endpoint returned a technically valid response,
     * the content of that response cannot be used as expected.
     * <p>
     * This exception is used internally to signal cases where:
     * <ul>
     *   <li>The HTTP request completed successfully (2xx status code)</li>
     *   <li>The response body is syntactically valid (e.g., valid JSON/XML)</li>
     *   <li>But the content is semantically invalid or doesn't meet business requirements</li>
     * </ul>
     *
     * <p>This is different from HTTP error status codes, as it indicates an issue with the
     * response content rather than the request or server state. It's typically thrown during
     * response processing, not by the {@code ApiClient} itself.
     */
    public static class ApiResponseUnusableException extends ApiClientException {
        /**
         * Creates a new ApiResponseUnusableException with the specified detail message.
         *
         * @param message the detail message (which is saved for later retrieval by the getMessage() method)
         */
        public ApiResponseUnusableException(String message) {
            super(message);
        }

        /**
         * Creates a new ApiResponseUnusableException with the specified detail message and cause.
         *
         * @param message the detail message (which is saved for later retrieval by the getMessage() method)
         * @param cause the cause (which is saved for later retrieval by the getCause() method)
         */
        public ApiResponseUnusableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception indicating that an API operation could not complete within the allowed time.
     * <p>
     * This exception is thrown in various timeout scenarios:
     * <ul>
     *   <li>When the maximum execution time for a request is exceeded</li>
     *   <li>When all retry attempts are exhausted</li>
     *   <li>When a request is explicitly canceled</li>
     *   <li>When a network timeout occurs during request execution</li>
     * </ul>
     *
     * <p>This is different from {@code HTTP_504_ServerTimeoutException} which specifically
     * represents an HTTP 504 Gateway Timeout response from the server.
     */
    public static class ApiTimeoutException extends ApiClientException {
        /**
         * Creates a new ApiTimeoutException with the specified detail message.
         *
         * @param message the detail message (which is saved for later retrieval by the getMessage() method)
         */
        public ApiTimeoutException(String message) {
            super(message);
        }

        /**
         * Creates a new ApiTimeoutException with the specified detail message and cause.
         *
         * @param message the detail message (which is saved for later retrieval by the getMessage() method)
         * @param cause the cause (which is saved for later retrieval by the getCause() method)
         */
        public ApiTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ---------------------------------------
    // Streaming-specific Exceptions
    // ---------------------------------------

    /**
     * Exception thrown when an error occurs during streaming operations.
     * 
     * <p>This exception is thrown for streaming-specific errors such as:
     * <ul>
     *   <li>Stream parsing errors</li>
     *   <li>Streaming protocol violations</li>
     *   <li>Stream-specific configuration errors</li>
     * </ul>
     */
    public static class StreamingException extends ApiClientException {
        /**
         * Creates a new StreamingException with the specified detail message.
         *
         * @param message the detail message
         */
        public StreamingException(String message) {
            super(message);
        }

        /**
         * Creates a new StreamingException with the specified detail message and cause.
         *
         * @param message the detail message
         * @param cause the cause of the exception
         */
        public StreamingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when a streaming connection fails or is interrupted.
     * 
     * <p>This exception is thrown when:
     * <ul>
     *   <li>The streaming connection is lost unexpectedly</li>
     *   <li>The server closes the stream prematurely</li>
     *   <li>Network issues interrupt the stream</li>
     * </ul>
     */
    public static class StreamingConnectionException extends StreamingException {
        /**
         * Creates a new StreamingConnectionException with the specified detail message.
         *
         * @param message the detail message
         */
        public StreamingConnectionException(String message) {
            super(message);
        }

        /**
         * Creates a new StreamingConnectionException with the specified detail message and cause.
         *
         * @param message the detail message
         * @param cause the cause of the exception
         */
        public StreamingConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when a streaming response is only partially received.
     * 
     * <p>This exception indicates that some data was received but the stream
     * did not complete normally. It may be possible to retry or resume the stream.
     */
    public static class StreamingPartialResponseException extends StreamingException {
        /**
         * The length of the partial data that was received before the stream ended.
         */
        private final int partialDataLength;

        /**
         * Creates a new StreamingPartialResponseException with the specified detail message.
         *
         * @param message the detail message
         * @param partialDataLength the amount of data received before the error
         */
        public StreamingPartialResponseException(String message, int partialDataLength) {
            super(message);
            this.partialDataLength = partialDataLength;
        }

        /**
         * Creates a new StreamingPartialResponseException with the specified detail message and cause.
         *
         * @param message the detail message
         * @param cause the cause of the exception
         * @param partialDataLength the amount of data received before the error
         */
        public StreamingPartialResponseException(String message, Throwable cause, int partialDataLength) {
            super(message, cause);
            this.partialDataLength = partialDataLength;
        }

        /**
         * Returns the amount of partial data that was received.
         *
         * @return the partial data length in bytes
         */
        public int getPartialDataLength() {
            return partialDataLength;
        }
    }

    /**
     * Exception thrown when a streaming operation times out.
     * 
     * <p>This is different from regular API timeouts as it specifically
     * relates to streaming operations that may have different timeout requirements.
     */
    public static class StreamingTimeoutException extends StreamingException {
        /**
         * Creates a new StreamingTimeoutException with the specified detail message.
         *
         * @param message the detail message
         */
        public StreamingTimeoutException(String message) {
            super(message);
        }

        /**
         * Creates a new StreamingTimeoutException with the specified detail message and cause.
         *
         * @param message the detail message
         * @param cause the cause of the exception
         */
        public StreamingTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ---------------------------------------
    // Streaming API Methods
    // ---------------------------------------

    /**
     * Sends a streaming API request without retry logic.
     * 
     * <p>This method processes streaming responses in real-time, calling the provided
     * handler as data chunks arrive from the server. It's suitable for applications
     * that need immediate access to partial results.
     * 
     * @param <T> The type of streaming request that extends ApiRequest
     * @param <U> The type of data chunks in the streaming response
     * @param request The streaming API request to send
     * @param handler The handler to process streaming data as it arrives
     * @return A CompletableFuture that completes when streaming is finished
     * @throws IllegalArgumentException if request is not a streaming request
     */





    /**
     * Executes a streaming operation with a timeout, matching the pattern of executeRequestWithTimeout.
     * This method provides timeout control for streaming operations to ensure they don't run indefinitely.
     *
     * @param <U> The type of the streaming result data
     * @param operation The streaming operation to execute
     * @param remainingMs The maximum time to wait for the operation to complete, in milliseconds
     * @return The result of the streaming operation
     * @throws StreamingTimeoutException if the operation times out
     * @throws StreamingException if the operation is interrupted or fails
     */
    protected <U> StreamingResult<U> executeStreamingWithTimeout(Supplier<StreamingResult<U>> operation, long remainingMs) {
        final CompletableFuture<StreamingResult<U>> future = CompletableFuture.supplyAsync(operation, HTTP_CLIENT_EXECUTOR);

        try {
            if (remainingMs > 0) {
                return future.get(remainingMs, TimeUnit.MILLISECONDS);
            }
            return future.get();
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new StreamingTimeoutException("Streaming request timed out during execution", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new StreamingException("Streaming request interrupted", ex);
        } catch (ExecutionException ex) {
            future.cancel(true);
            if (ex.getCause() != null) {
                if (ex.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) ex.getCause();
                }
                throw new StreamingException("Streaming execution failed", ex.getCause());
            }
            throw new StreamingException("Streaming execution failed", ex);
        }
    }

    /**
     * Creates a new StreamingTimeoutException with details about the timeout, matching createTimeoutException.
     * This method provides consistent error messaging for streaming timeout scenarios.
     *
     * @param reason The reason for the timeout (may be null)
     * @param maxDurationSeconds The maximum duration that was allowed in seconds
     * @param cause The cause of the timeout (may be null)
     * @return A new StreamingTimeoutException instance with formatted message
     */
    protected StreamingTimeoutException createStreamingTimeoutException(
            String reason,
            int maxDurationSeconds,
            Throwable cause
    ) {
        String msg = "Maximum streaming execution time of " + maxDurationSeconds + "s reached!";
        if (reason != null) msg += " " + reason;
        return new StreamingTimeoutException(msg, cause);
    }
    
    // ---------------------------------------
    // New Streaming Methods for 2.1.0
    // ---------------------------------------
    
    /**
     * Executes a streaming request asynchronously without retry.
     * This method is used internally by the new execute methods.
     * 
     * @param <T> The type of the API request that extends ApiRequest
     * @param request The API request to execute as a stream
     * @return A CompletableFuture containing the API response with streaming context as CompletableFuture<? extends ApiResponse<?>>
     * @since 2.1.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T extends ApiRequest<?>> CompletableFuture<? extends ApiResponse<?>> executeStreamingAsync(T request) {
        StreamingInfo streamingInfo = request.getStreamingInfo();
        if (streamingInfo == null || !streamingInfo.isEnabled()) {
            throw new IllegalArgumentException("Request is not configured for streaming");
        }
        
        if (settings.getBeforeSendAction() != null) {
            settings.getBeforeSendAction().accept(request);
        }
        
        long startMillis = System.currentTimeMillis();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Run the streaming request and collect results
                de.entwicklertraining.api.base.streaming.StreamingExecutionContext<T, Object> context = new de.entwicklertraining.api.base.streaming.StreamingExecutionContext<>();
                StreamingResult<Object> result = runStreamingRequestNew(
                    request, 
                    (StreamingResponseHandler<Object>) streamingInfo.getHandler(),
                    streamingInfo,
                    context
                );
                
                // Create streaming context from result
                long endMillis = System.currentTimeMillis();
                StreamingContext<Object> streamingContext = StreamingContext.fromStreamingResult(
                    result,
                    context.getChunks(),
                    context.getMetadata(),
                    startMillis,
                    endMillis
                );
                
                // Create response with streaming context - use empty JSON for streaming
                ApiResponse response = request.createResponse("{}");
                response.setStreamingContext(streamingContext);
                
                return (ApiResponse<?>) response;
                
            } catch (Exception e) {
                throw new StreamingException("Streaming execution failed", e);
            }
        }, HTTP_CLIENT_EXECUTOR);
    }
    
    /**
     * Executes a streaming request asynchronously with retry.
     * This method is used internally by the new execute methods.
     * 
     * @param <T> The type of the API request that extends ApiRequest
     * @param request The API request to execute as a stream
     * @return A CompletableFuture containing the API response with streaming context as CompletableFuture<? extends ApiResponse<?>>
     * @since 2.1.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T extends ApiRequest<?>> CompletableFuture<? extends ApiResponse<?>> executeStreamingAsyncWithRetry(T request) {
        StreamingInfo streamingInfo = request.getStreamingInfo();
        if (streamingInfo == null || !streamingInfo.isEnabled()) {
            throw new IllegalArgumentException("Request is not configured for streaming");
        }
        
        if (settings.getBeforeSendAction() != null) {
            settings.getBeforeSendAction().accept(request);
        }
        
        long startMillis = System.currentTimeMillis();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Execute with retry logic
                de.entwicklertraining.api.base.streaming.StreamingExecutionContext<T, Object> context = new de.entwicklertraining.api.base.streaming.StreamingExecutionContext<>();
                StreamingResult<Object> result = executeStreamingWithRetryNew(
                    () -> runStreamingRequestNew(
                        request,
                        (StreamingResponseHandler<Object>) streamingInfo.getHandler(),
                        streamingInfo,
                        context
                    ),
                    request
                );
                
                // Create streaming context from result
                long endMillis = System.currentTimeMillis();
                StreamingContext<Object> streamingContext = StreamingContext.fromStreamingResult(
                    result,
                    context.getChunks(),
                    context.getMetadata(),
                    startMillis,
                    endMillis
                );
                
                // Create response with streaming context - use empty JSON for streaming
                ApiResponse response = request.createResponse("{}");
                response.setStreamingContext(streamingContext);
                
                return (ApiResponse<?>) response;
                
            } catch (Exception e) {
                throw new StreamingException("Streaming execution with retry failed", e);
            }
        }, HTTP_CLIENT_EXECUTOR);
    }

    /**
     * Executes a streaming request with the new architecture (without StreamingApiRequest).
     * This method handles the actual HTTP streaming request execution including cancellation monitoring.
     * 
     * @param <T> The type of request that extends ApiRequest
     * @param <U> The type of data chunks in the streaming response
     * @param request The API request to execute as a stream
     * @param handler The streaming response handler to process incoming data
     * @param streamingInfo The streaming configuration including format and settings
     * @param context The execution context for collecting streaming data and metadata
     * @return The streaming result containing completion status and error information
     * @throws StreamingException if the streaming request fails
     * @since 2.1.0
     */
    private <T extends ApiRequest<?>, U> StreamingResult<U> runStreamingRequestNew(
            T request,
            StreamingResponseHandler<U> handler,
            StreamingInfo streamingInfo,
            de.entwicklertraining.api.base.streaming.StreamingExecutionContext<T, U> context) throws StreamingException {
        
        // Check for cancellation at start
        if (request.getIsCanceledSupplier().get()) {
            throw new CancellationException("Streaming request was cancelled before execution");
        }
        
        String fullUrl = getBaseUrl() + request.getRelativeUrl();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header("Content-Type", request.getContentType())
                .header("Accept", streamingInfo.getFormat().getAcceptHeader())
                .header("Cache-Control", "no-cache");
        
        // Apply global headers from HTTP configuration
        httpConfig.getGlobalHeaders().forEach(builder::header);
        
        // Apply request modifiers from HTTP configuration
        httpConfig.getRequestModifiers().forEach(modifier -> modifier.accept(builder));
        
        // Apply request-specific headers
        for (Map.Entry<String, String> entry : request.getAdditionalHeaders().entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        
        // Add streaming-specific headers from config
        StreamingConfig config = streamingInfo.getConfigOrDefault();
        if (config != null && config.getLastEventId().isPresent()) {
            builder.header("Last-Event-ID", config.getLastEventId().get());
        }
        
        // Set HTTP method
        String method = request.getHttpMethod().toUpperCase();
        switch (method) {
            case "POST" -> {
                if (request.getContentType().startsWith("multipart/form-data")) {
                    byte[] bodyBytes = request.getBodyBytes();
                    builder.POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
                } else {
                    builder.POST(HttpRequest.BodyPublishers.ofString(request.getBody()));
                }
            }
            case "GET" -> builder.GET();
            case "DELETE" -> builder.DELETE();
            default -> throw new StreamingException("Unsupported HTTP method for streaming: " + method);
        }
        
        HttpRequest httpRequest = builder.build();
        
        CompletableFuture<Void> cancelWatcher = null;
        try {
            // Send streaming request
            CompletableFuture<HttpResponse<Stream<String>>> future = 
                httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofLines());
            
            // Set up cancellation watcher
            cancelWatcher = CompletableFuture.runAsync(() -> {
                try {
                    while (!future.isDone()) {
                        if (request.getIsCanceledSupplier().get() || handler.shouldCancel()) {
                            future.cancel(true);
                            break;
                        }
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, CANCEL_WATCHER_EXECUTOR);
            
            // Get the response
            HttpResponse<Stream<String>> response;
            if (request.getMaxExecutionTimeInSeconds() > 0) {
                response = future.get(request.getMaxExecutionTimeInSeconds(), TimeUnit.SECONDS);
            } else {
                response = future.get();
            }
            
            int statusCode = response.statusCode();
            if (statusCode != 200) {
                // Read the error response body for better error messages
                String errorBody = "";
                try (Stream<String> lines = response.body()) {
                    errorBody = lines.collect(java.util.stream.Collectors.joining("\n"));
                } catch (Exception e) {
                    errorBody = "(could not read error body: " + e.getMessage() + ")";
                }

                // Handle non-200 status codes with full error details
                String errorMessage = "Streaming request failed with status " + statusCode + ": " + errorBody;
                StatusCodeExceptionRegistration reg = statusCodeExceptions.get(statusCode);
                if (reg != null) {
                    throw createException(reg, errorMessage);
                }
                throw new StreamingConnectionException(errorMessage);
            }
            
            // Process the streaming response
            return processStreamingResponseNew(request, response, handler, streamingInfo, context);
            
        } catch (CancellationException e) {
            throw e; // Re-throw to allow consistent cancellation handling
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamingException("Streaming request was interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() != null) {
                throw new StreamingException("Streaming request execution failed", e.getCause());
            }
            throw new StreamingException("Streaming request execution failed", e);
        } catch (TimeoutException e) {
            throw new StreamingTimeoutException("Streaming request timed out after " + request.getMaxExecutionTimeInSeconds() + " seconds", e);
        } finally {
            // Cancel the watcher
            if (cancelWatcher != null) {
                cancelWatcher.cancel(true);
            }
        }
    }
    
    /**
     * Processes the streaming HTTP response with the new architecture.
     * This method handles line-by-line processing of streaming data using the appropriate processor.
     * 
     * @param <T> The type of request that extends ApiRequest
     * @param <U> The type of data chunks in the streaming response  
     * @param request The original request that initiated the stream
     * @param response The HTTP response containing the streaming data as a Stream of lines
     * @param handler The response handler to process each data chunk
     * @param streamingInfo The streaming configuration including format settings
     * @param context The execution context for collecting streaming data and metadata
     * @return The streaming result indicating completion status and any errors
     * @since 2.1.0
     */
    private <T extends ApiRequest<?>, U> StreamingResult<U> processStreamingResponseNew(
            T request,
            HttpResponse<Stream<String>> response,
            StreamingResponseHandler<U> handler,
            StreamingInfo streamingInfo,
            de.entwicklertraining.api.base.streaming.StreamingExecutionContext<T, U> context) {
        
        StreamProcessor<U> processor = StreamProcessorFactory.createProcessor(
            streamingInfo.getFormat(),
            (Class<U>) String.class  // Use String.class to enable OPENAI_STYLE extraction
        );
        
        StreamingResult<U> result = new StreamingResult<>();
        boolean streamCompleted = false;
        int linesProcessed = 0;
        
        handler.onStreamStart();
        
        try (Stream<String> lines = response.body()) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (Thread.currentThread().isInterrupted() || handler.shouldCancel()) {
                    handler.onError(new StreamingException("Stream was canceled"));
                    result.setCanceled(true);
                    break;
                }
                
                try {
                    processor.processLine(line, new StreamingResponseHandler<U>() {
                        @Override
                        public void onStreamStart() {}
                        
                        @Override
                        public void onData(U data) {
                            handler.onData(data);
                            context.addChunk(data);
                        }
                        
                        @Override
                        public void onChunk(U chunk) {
                            handler.onChunk(chunk);
                            context.addChunk(chunk);
                        }
                        
                        @Override
                        public void onMetadata(Map<String, Object> metadata) {
                            handler.onMetadata(metadata);
                            metadata.forEach(context::addMetadata);
                        }
                        
                        @Override
                        public void onComplete() {
                            handler.onComplete();
                        }
                        
                        @Override
                        public void onError(Throwable error) {
                            handler.onError(error);
                        }
                        
                        @Override
                        public boolean shouldCancel() {
                            return handler.shouldCancel();
                        }
                    });
                    linesProcessed++;
                    
                    if (processor.isCompletionLine(line)) {
                        streamCompleted = true;
                        break;
                    }
                    
                } catch (StreamProcessor.StreamProcessingException e) {
                    logger.warn("Error processing streaming line: {}", e.getMessage());
                    // Continue processing other lines
                } catch (Exception e) {
                    logger.error("Unexpected error processing streaming line", e);
                    handler.onError(e);
                    result.setError(e);
                    break;
                }
            }
            
            if (streamCompleted) {
                handler.onComplete();
                result.setCompleted(true);
            } else if (!result.isCanceled() && result.getError() == null) {
                // Stream ended without completion signal
                StreamingPartialResponseException partialException = 
                    new StreamingPartialResponseException("Stream ended without completion signal", linesProcessed);
                handler.onError(partialException);
                result.setError(partialException);
            }
            
        } catch (Exception e) {
            StreamingConnectionException connectionException = 
                new StreamingConnectionException("Error reading streaming response", e);
            handler.onError(connectionException);
            result.setError(connectionException);
        }
        
        result.setLinesProcessed(linesProcessed);
        return result;
    }
    
    /**
     * Executes a streaming operation with retry logic using exponential backoff (new architecture).
     * This method applies the same retry logic as regular requests to streaming operations.
     * 
     * @param <U> The type of streaming result data
     * @param operation The streaming operation to execute with retry logic
     * @param request The request being processed (used for timeout and cancellation checks)
     * @return The streaming result from the successful operation
     * @throws StreamingException if maximum retries are exceeded or operation fails permanently
     * @since 2.1.0
     */
    private <U> StreamingResult<U> executeStreamingWithRetryNew(
            Supplier<StreamingResult<U>> operation,
            ApiRequest<?> request) throws StreamingException {
        
        final long startTimeMs = System.currentTimeMillis();
        final int maxDurationSeconds = request.getMaxExecutionTimeInSeconds();
        final long maxDurationMs = maxDurationSeconds * 1000L;
        
        String latestReason = null;
        Throwable latestException = null;
        
        for (int attempt = 1; attempt <= settings.getMaxRetries(); attempt++) {
            try {
                long elapsedMs = System.currentTimeMillis() - startTimeMs;
                long remainingMs = maxDurationMs - elapsedMs;
                
                if (maxDurationMs > 0 && remainingMs <= 0) {
                    throw new StreamingTimeoutException(
                        "Maximum streaming duration of " + maxDurationSeconds + "s exceeded. " + latestReason);
                }
                
                return operation.get();
                
            } catch (Throwable ex) {
                if (isRetryableStreamingException(ex)) {
                    latestReason = "Retriable streaming error: " + ex.getMessage();
                    latestException = ex;
                } else {
                    // Non-retriable error, throw immediately
                    if (ex instanceof StreamingException) {
                        throw (StreamingException) ex;
                    }
                    throw new StreamingException("Non-retriable streaming error", ex);
                }
            }
            
            if (attempt == settings.getMaxRetries()) {
                throw new StreamingException(
                    "Maximum streaming retries of " + settings.getMaxRetries() + " exhausted. " + latestReason,
                    latestException);
            }
            
            // Apply exponential backoff before retry
            long elapsedMs = System.currentTimeMillis() - startTimeMs;
            long remainingMs = maxDurationMs - elapsedMs;
            if (maxDurationMs > 0 && remainingMs <= 0) {
                throw new StreamingTimeoutException(
                    "Maximum streaming duration of " + maxDurationSeconds + "s exceeded during retry backoff");
            }
            
            long durationOfNextSleep = calculateNextSleep(settings.getInitialDelayMs());
            durationOfNextSleep = maybeAdjustSleepForFinalRetry(maxDurationSeconds, durationOfNextSleep, remainingMs);
            
            try {
                applySleep(durationOfNextSleep, remainingMs);
            } catch (ApiTimeoutException e) {
                throw new StreamingTimeoutException("Streaming retry timeout", e);
            }
        }
        
        throw new StreamingException("Streaming retry logic exhausted unexpectedly.");
    }

    /**
     * Creates a new StreamingException indicating that all retry attempts were exhausted, matching createRetriesExhaustedException.
     * This method provides consistent error messaging when streaming retry limits are reached.
     *
     * @param reason The reason for the failure (may be null)
     * @param cause The cause of the failure (may be null)
     * @return A new StreamingException instance with formatted message including retry count
     */
    protected StreamingException createStreamingRetriesExhaustedException(
            String reason,
            Throwable cause
    ) {
        String msg = "Maximum streaming retries of " + settings.getMaxRetries() + " exhausted!";
        if (reason != null) msg += " " + reason;
        return new StreamingException(msg, cause);
    }

    /**
     * Checks if an exception is retriable for streaming operations.
     * This method determines whether a streaming error should trigger a retry attempt.
     * Connection errors, timeouts, and partial responses are generally retriable.
     * 
     * @param ex The exception to check for retry eligibility
     * @return true if the exception should trigger a retry, false otherwise
     */
    private boolean isRetryableStreamingException(Throwable ex) {
        // Connection errors are retriable
        if (ex instanceof StreamingConnectionException) {
            return true;
        }
        
        // Timeout errors are retriable
        if (ex instanceof StreamingTimeoutException) {
            return true;
        }
        
        // Partial responses might be retriable depending on configuration
        if (ex instanceof StreamingPartialResponseException) {
            return true;
        }
        
        // General streaming exceptions are not retriable by default
        if (ex instanceof StreamingException) {
            return false;
        }
        
        // Check if it matches any registered retriable status code exceptions
        return isRetryableException(ex);
    }

    /**
     * Result class for streaming operations.
     * This class encapsulates the outcome of a streaming request including completion status,
     * cancellation state, error information, and processing statistics.
     * 
     * @param <T> The type of data being streamed
     */
    public static class StreamingResult<T> {
        
        /**
         * Default constructor for StreamingResult.
         */
        public StreamingResult() {}
        
        private boolean completed = false;
        private boolean canceled = false;
        private Throwable error = null;
        private int linesProcessed = 0;

        /**
         * Returns whether the streaming operation has completed.
         * 
         * @return true if the operation has completed, false otherwise
         */
        public boolean isCompleted() { return completed; }
        /**
         * Sets the completion status of the streaming operation.
         * 
         * @param completed true if the operation has completed, false otherwise
         */
        public void setCompleted(boolean completed) { this.completed = completed; }

        /**
         * Returns whether the streaming operation was canceled.
         * 
         * @return true if the operation was canceled, false otherwise
         */
        public boolean isCanceled() { return canceled; }
        /**
         * Sets the cancellation status of the streaming operation.
         * 
         * @param canceled true if the operation was canceled, false otherwise
         */
        public void setCanceled(boolean canceled) { this.canceled = canceled; }

        /**
         * Returns any error that occurred during the streaming operation.
         * 
         * @return the error that occurred, or null if no error occurred
         */
        public Throwable getError() { return error; }
        /**
         * Sets the error that occurred during the streaming operation.
         * 
         * @param error the error that occurred, or null if no error occurred
         */
        public void setError(Throwable error) { this.error = error; }

        /**
         * Returns the number of lines processed during the streaming operation.
         * 
         * @return the number of lines processed
         */
        public int getLinesProcessed() { return linesProcessed; }
        /**
         * Sets the number of lines processed during the streaming operation.
         * 
         * @param linesProcessed the number of lines processed
         */
        public void setLinesProcessed(int linesProcessed) { this.linesProcessed = linesProcessed; }

        /**
         * Returns whether the streaming operation completed successfully.
         * A successful operation is one that completed without being canceled and without errors.
         * 
         * @return true if the operation completed successfully, false otherwise
         */
        public boolean isSuccess() { 
            return completed && !canceled && error == null; 
        }
    }

    // ---------------------------------------
    // Helper classes and methods for capturing data
    // ---------------------------------------
    
    /**
     * Stores capture data for API call monitoring and debugging.
     * This method is used internally to capture request/response data when configured.
     *
     * @param <T> The type of the API request that extends ApiRequest
     * @param <U> The type of the API response that extends ApiResponse
     * @param request The API request that was executed
     * @param captureConsumer The consumer to handle the captured data
     * @param finalResponse The response received (may be null on error)
     * @param context The execution context containing request/response data
     * @param finalException The exception that occurred (may be null on success)
     * @param start The start time of the request
     * @param end The end time of the request
     * @param success Whether the request was successful
     */
    private <T extends ApiRequest<U>, U extends ApiResponse<T>> void storeCaptureData(
            T request,
            Consumer<ApiCallCaptureInput> captureConsumer,
            U finalResponse,
            ApiRequestExecutionContext<T, U> context,
            RuntimeException finalException,
            Instant start,
            Instant end,
            boolean success
    ) {
        Throwable storeEx = finalException;
        if (storeEx instanceof ApiTimeoutException ate && ate.getCause() != null) {
            storeEx = ate.getCause();
        }

        String exceptionClass = null;
        String exceptionMessage = null;
        String exceptionStacktrace = null;

        if (!success && storeEx != null) {
            exceptionClass = storeEx.getClass().getName();
            exceptionMessage = storeEx.getMessage();
            exceptionStacktrace = getStackTraceAsString(storeEx);
        }

        // Input data: request.getBody() if not binary, else we can do something minimal
        String inputData;
        if (request.isBinaryResponse() && request.getBodyBytes() != null) {
            // For capturing the data the request was *sending* in binary form:
            // If there's a separate method for that, we could store it.
            // We'll just store "binary body" for minimalism.
            inputData = "binary body (size=" + request.getBodyBytes().length + ")";
        } else {
            inputData = request.getBody();
        }

        // Output data from context
        String outputData = null;
        if (success) {
            if (context.getResponseBody() != null) {
                outputData = context.getResponseBody();
            } else if (context.getResponseBytes() != null) {
                outputData = "binary response (size=" + context.getResponseBytes().length + ")";
            }
        } else {
            if (context.getResponseBody() != null) {
                outputData = context.getResponseBody();
            } else if (context.getResponseBytes() != null) {
                outputData = "binary response (size=" + context.getResponseBytes().length + ")";
            }
        }

        captureConsumer.accept(new ApiCallCaptureInput(
                start,
                end,
                success,
                exceptionClass,
                exceptionMessage,
                exceptionStacktrace,
                inputData,
                outputData
        ));
    }

    /**
     * Converts a throwable's stack trace to a string representation.
     * This method is used internally for capturing exception details.
     *
     * @param throwable The throwable to convert
     * @return The stack trace as a formatted string
     */
    private String getStackTraceAsString(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.toString()).append("\n");
        for (StackTraceElement elem : throwable.getStackTrace()) {
            sb.append("\tat ").append(elem.toString()).append("\n");
        }
        return sb.toString();
    }
}

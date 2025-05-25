package de.entwicklertraining.api.base;

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

    protected final HttpClient httpClient;
    private final ApiClientSettings settings;
    private Optional<String> baseUrl = Optional.empty();
    private boolean statusCodeExceptionsWarningLogged = false;

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

    protected ApiClient(ApiClientSettings settings) {
        // HttpClient an Virtual-Thread-Executor binden:
        this.httpClient = HttpClient.newBuilder()
                .executor(HTTP_CLIENT_EXECUTOR)
                .build();
        this.settings = settings;
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
     * @param <T> The type of the API request
     * @param <U> The type of the API response
     * @param request The API request to send
     * @return The API response
     * @throws ApiTimeoutException if the request times out or maximum retries are exceeded
     * @throws ApiClientException if there is an error executing the request
     */
    public <T extends ApiRequest<U>, U extends ApiResponse<T>> U sendRequestWithExponentialBackoff(T request) {
        long startMillis = System.currentTimeMillis();
        Instant startInstant = Instant.ofEpochMilli(startMillis);

        boolean success = false;
        RuntimeException finalException = null;
        U finalResponse = null;

        // Create a context for capturing response data
        ApiRequestExecutionContext<T, U> context = new ApiRequestExecutionContext<>();

        try {
            finalResponse = executeWithRetry(() -> runRequest(request, context), request);
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
     * Sends an API request without automatic retry logic.
     * This method will not retry failed requests, making it suitable for non-idempotent operations.
     *
     * @param <T> The type of the API request
     * @param <U> The type of the API response
     * @param request The API request to send
     * @return The API response
     * @throws ApiTimeoutException if the request times out
     * @throws ApiClientException if there is an error executing the request
     */
    public <T extends ApiRequest<U>, U extends ApiResponse<T>> U sendRequest(T request) {
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
            finalResponse = executeWithTimeout(() -> runRequest(request, context), maxDurationMs);
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
     * @param <T> The type of the API request
     * @param <U> The type of the API response
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

        if (this.settings.getBearerAuthenticationKey().isPresent()) {
            builder = builder.header("Authorization", "Bearer " + this.settings.getBearerAuthenticationKey().get());
        }

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

        } catch (CancellationException cex) {
            // Future gecanceled => Abbruch
            throw new ApiTimeoutException("Request was canceled", cex);

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
     * @param <U> The type of the operation result
     * @param operation The operation to execute
     * @param request The API request being processed
     * @return The result of the operation
     * @throws ApiTimeoutException if maximum retries are exceeded or the operation times out
     */
    protected <U extends ApiResponse<?>> U executeWithRetry(Supplier<U> operation, ApiRequest<?> request) {
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
                    throw createTimeoutException(maxDurationSeconds, latestReason, latestException);
                }

                return executeWithTimeout(operation, remainingMs);

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
                throw createTimeoutException(maxDurationSeconds, latestReason, latestException);
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
    protected <U> U executeWithTimeout(Supplier<U> operation, long remainingMs) {
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

    protected long calculateNextSleep(long currentDelay) {
        double factor = settings.getExponentialBase();
        if (settings.isUseJitter()) {
            factor *= (1.0 + Math.random());
        }
        return (long) (currentDelay * factor);
    }

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

    protected ApiTimeoutException createTimeoutException(
            int maxDurationSeconds,
            String reason,
            Throwable cause
    ) {
        String msg = "Maximum execution time of " + maxDurationSeconds + "s reached!";
        if (reason != null) msg += " " + reason;
        return new ApiTimeoutException(msg, cause);
    }

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

        private ExceptionType type;

        public HTTP_429_RateLimitOrQuotaException(String message) {
            super(message);
            this.type = ExceptionType.Unknown;
        }

        public HTTP_429_RateLimitOrQuotaException(String message, Throwable cause) {
            super(message, cause);
            this.type = ExceptionType.RateLimit;
        }

        public void setType(ExceptionType type) {
            this.type = type;
        }

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
        public HTTP_400_RequestRejectedException(String message) {
            super(message);
        }

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
        public HTTP_401_AuthorizationException(String message) {
            super(message);
        }

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
        public HTTP_402_PaymentRequiredException(String message) {
            super(message);
        }

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
        public HTTP_403_PermissionDeniedException(String message) {
            super(message);
        }

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
        public HTTP_404_NotFoundException(String message) {
            super(message);
        }

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
        public HTTP_422_UnprocessableEntityException(String message) {
            super(message);
        }

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
        public HTTP_500_ServerErrorException(String message) {
            super(message);
        }

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
        public HTTP_503_ServerUnavailableException(String message) {
            super(message);
        }

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
        public HTTP_504_ServerTimeoutException(String message) {
            super(message);
        }

        public HTTP_504_ServerTimeoutException(String message, Throwable cause) {
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
    public static class ApiResponseUnusableException extends RuntimeException {
        public ApiResponseUnusableException(String message) {
            super(message);
        }

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
    public static class ApiTimeoutException extends RuntimeException {
        public ApiTimeoutException(String message) {
            super(message);
        }

        public ApiTimeoutException(String message, Throwable cause) {
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
        public ApiClientException(String message) {
            super(message);
        }

        public ApiClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ---------------------------------------
    // Helper method for capturing data
    // ---------------------------------------
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

    private String getStackTraceAsString(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.toString()).append("\n");
        for (StackTraceElement elem : throwable.getStackTrace()) {
            sb.append("\tat ").append(elem.toString()).append("\n");
        }
        return sb.toString();
    }
}

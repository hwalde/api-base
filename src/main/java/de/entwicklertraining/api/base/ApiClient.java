package de.entwicklertraining.api.base;

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

/**
 * Abstract base class for a generic API client with
 * standard HTTP requests and exponential backoff retry mechanism.
 *
 * This class does not contain specific logic for a particular service.
 * Concrete clients (e.g., "GeminiClient") inherit and can
 * use 'registerStatusCodeException' for error handling.
 */
public abstract class ApiClient {

    // Nun Virtual Threads für den HttpClient
    private static final ExecutorService HTTP_CLIENT_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    // Auch der Cancel-Watcher läuft in virtuellen Threads
    private static final ExecutorService CANCEL_WATCHER_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    protected final HttpClient httpClient;
    private final ApiClientSettings settings;

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

    protected ApiClient(ApiClientSettings settings) {
        // HttpClient an Virtual-Thread-Executor binden:
        this.httpClient = HttpClient.newBuilder()
                .executor(HTTP_CLIENT_EXECUTOR)
                .build();
        this.settings = settings;
    }

    protected final void registerStatusCodeException(
            int statusCode,
            Class<? extends RuntimeException> exceptionClass,
            String message,
            boolean retry
    ) {
        statusCodeExceptions.put(statusCode, new StatusCodeExceptionRegistration(exceptionClass, message, retry));
    }

    /**
     * Öffentliche Methode, um einen Request abzusetzen. Führt ggf. Retries durch.
     */
    public <T extends ApiRequest<U>, U extends ApiResponse<T>> U sendRequest(T request) {
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
     * Öffentliche Methode, um einen Request abzusetzen. Führt keine(!) Retries durch.
     */
    public <T extends ApiRequest<U>, U extends ApiResponse<T>> U sendRequestWithoutExponentialBackoff(T request) {
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
     * Führt einen einzelnen HTTP-Request asynchron aus (per CompletableFuture).
     * Falls Thread unterbrochen ist oder das Future gecanceled wird, bricht der Aufruf ab.
     */
    protected <T extends ApiRequest<U>, U extends ApiResponse<T>> U runRequest(T request,
                                                                               ApiRequestExecutionContext<T, U> context) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(request.getUri()))
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
     * Führt den Request mit Exponential Backoff aus.
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
     * Schläft für 'delayMs', falls Zeit dafür vorhanden ist.
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

    public static class HTTP_429_RateLimitOrQuotaException extends RuntimeException {
        public enum ExceptionType { Unknown, RateLimit, Quota }

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

    public static class HTTP_400_RequestRejectedException extends RuntimeException {
        public HTTP_400_RequestRejectedException(String message) {
            super(message);
        }

        public HTTP_400_RequestRejectedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class HTTP_401_AuthorizationException extends RuntimeException {
        public HTTP_401_AuthorizationException(String message) {
            super(message);
        }

        public HTTP_401_AuthorizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class HTTP_402_PaymentRequiredException extends RuntimeException {
        public HTTP_402_PaymentRequiredException(String message) {
            super(message);
        }

        public HTTP_402_PaymentRequiredException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class HTTP_403_PermissionDeniedException extends RuntimeException {
        public HTTP_403_PermissionDeniedException(String message) {
            super(message);
        }

        public HTTP_403_PermissionDeniedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class HTTP_404_NotFoundException extends RuntimeException {
        public HTTP_404_NotFoundException(String message) {
            super(message);
        }

        public HTTP_404_NotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class HTTP_422_UnprocessableEntityException extends RuntimeException {
        public HTTP_422_UnprocessableEntityException(String message) {
            super(message);
        }

        public HTTP_422_UnprocessableEntityException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class HTTP_500_ServerErrorException extends RuntimeException {
        public HTTP_500_ServerErrorException(String message) {
            super(message);
        }

        public HTTP_500_ServerErrorException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class HTTP_503_ServerUnavailableException extends RuntimeException {
        public HTTP_503_ServerUnavailableException(String message) {
            super(message);
        }

        public HTTP_503_ServerUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class HTTP_504_ServerTimeoutException extends RuntimeException {
        public HTTP_504_ServerTimeoutException(String message) {
            super(message);
        }

        public HTTP_504_ServerTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ApiResponseUnusableException extends RuntimeException {
        public ApiResponseUnusableException(String message) {
            super(message);
        }

        public ApiResponseUnusableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ApiTimeoutException extends RuntimeException {
        public ApiTimeoutException(String message) {
            super(message);
        }

        public ApiTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ApiClientException extends RuntimeException {
        public ApiClientException(String message) {
            super(message);
        }

        public ApiClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ---------------------------------------
    // Hilfsmethode, um das Capture abzulegen
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

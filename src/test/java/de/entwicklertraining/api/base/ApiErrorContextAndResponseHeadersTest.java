package de.entwicklertraining.api.base;

import com.sun.net.httpserver.HttpServer;
import de.entwicklertraining.api.base.streaming.StreamingFormat;
import de.entwicklertraining.api.base.streaming.StreamingResponseHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the 2.3.0 error-context and response-header surface: the
 * {@link ApiErrorContext} the transport records on thrown exceptions, the response headers
 * it records on parsed responses (regular and streaming), and the opt-in
 * {@code Retry-After} backoff.
 */
public class ApiErrorContextAndResponseHeadersTest {

    private static final String ERROR_BODY =
            "{\"error\":{\"message\":\"rate limited\",\"code\":429,"
                    + "\"metadata\":{\"reason\":\"in_flight_budget\",\"retryable\":true,"
                    + "\"error_type\":\"rate_limit\",\"provider_code\":\"upstream_429\","
                    + "\"limit_source\":\"openrouter_in_flight_budget\"}}}";

    private static HttpServer server;
    private static int port;

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/error429", exchange -> {
            exchange.getResponseHeaders().add("Retry-After", "7");
            exchange.getResponseHeaders().add("X-RateLimit-Remaining", "0");
            exchange.getResponseHeaders().add("X-Multi", "a");
            exchange.getResponseHeaders().add("X-Multi", "b");
            byte[] body = ERROR_BODY.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/teapot", exchange -> {
            exchange.getResponseHeaders().add("X-Generation-Id", "gen_teapot");
            byte[] body = "{\"error\":\"short and stout\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(418, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/ok", exchange -> {
            exchange.getResponseHeaders().add("X-Generation-Id", "gen_123");
            exchange.getResponseHeaders().add("Retry-After", "not-sent-on-success");
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/sse", exchange -> {
            exchange.getResponseHeaders().add("X-Generation-Id", "gen_stream");
            byte[] body = ("data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\n\n"
                    + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/sse429", exchange -> {
            exchange.getResponseHeaders().add("Retry-After", "9");
            byte[] body = ERROR_BODY.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    // ------------------------------------------------------------------ error context

    @Test
    @DisplayName("a registered exception receives the error context: status, body and headers")
    void errorContextOnRegisteredException() {
        TestApiClient client = new TestApiClient(false);
        client.register(429, ContextException.class, "Test error", false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> client.execute(new TestRequest("/error429")));

        assertInstanceOf(ContextException.class, ex);
        assertEquals("Test error: " + ERROR_BODY, ex.getMessage());
        ApiErrorContext ctx = ((ContextException) ex).getApiErrorContext();
        assertNotNull(ctx);
        assertEquals(429, ctx.statusCode());
        assertEquals(ERROR_BODY, ctx.responseBody());
        assertEquals("7", ctx.header("Retry-After"));
        assertEquals("7", ctx.header("retry-after"), "header lookup is case-insensitive");
        assertEquals("0", ctx.header("X-RateLimit-Remaining"));
        assertEquals("a", ctx.header("X-Multi"), "a repeated header keeps its first value");
        assertNull(ctx.header("X-Not-Sent"));
        assertNull(ctx.header(null));
        assertEquals(ctx, ApiErrorContext.of(ex), "of() finds the context on an aware exception");
    }

    @Test
    @DisplayName("a message-only (legacy) exception class is still instantiated")
    void messageOnlyRegistrationStillWorks() {
        TestApiClient client = new TestApiClient(false);
        client.register(429, LegacyException.class, "Legacy error", false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> client.execute(new TestRequest("/error429")));

        assertInstanceOf(LegacyException.class, ex);
        assertEquals("Legacy error: " + ERROR_BODY, ex.getMessage());
        assertNull(ApiErrorContext.of(ex));
    }

    @Test
    @DisplayName("an unregistered status throws ApiClientException carrying the error context")
    void errorContextOnUnregisteredStatus() {
        TestApiClient client = new TestApiClient(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> client.execute(new TestRequest("/teapot")));

        assertInstanceOf(ApiClient.ApiClientException.class, ex);
        ApiErrorContext ctx = ((ApiClient.ApiClientException) ex).getApiErrorContext();
        assertNotNull(ctx);
        assertEquals(418, ctx.statusCode());
        assertEquals("{\"error\":\"short and stout\"}", ctx.responseBody());
        assertEquals("gen_teapot", ctx.header("X-Generation-Id"));
    }

    @Test
    @DisplayName("a streaming error response carries the error context too")
    void errorContextOnStreamingError() {
        TestApiClient client = new TestApiClient(false);
        client.register(429, ContextException.class, "Test error", false);

        AtomicReference<String> data = new AtomicReference<>();
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> client.execute(streamingRequest("/sse429", data::set)));

        // Pre-existing api-base behaviour: streaming errors surface wrapped (the async
        // execution layer wraps them) instead of as the registered exception itself. The
        // registered exception with its context is reachable through the cause chain.
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        assertInstanceOf(ContextException.class, root);
        assertEquals("Test error: Streaming request failed with status 429: " + ERROR_BODY,
                root.getMessage(), "the streaming message composition is unchanged");
        ApiErrorContext ctx = ((ContextException) root).getApiErrorContext();
        assertNotNull(ctx);
        assertEquals(429, ctx.statusCode());
        assertEquals(ERROR_BODY, ctx.responseBody());
        assertEquals("9", ctx.header("Retry-After"));
    }

    // ------------------------------------------------------------- response headers

    @Test
    @DisplayName("a parsed response carries the response headers")
    void responseHeadersOnSuccess() {
        TestApiClient client = new TestApiClient(false);

        ApiResponse<?> response = client.execute(new TestRequest("/ok"));

        assertEquals("gen_123", response.getResponseHeader("X-Generation-Id"));
        assertEquals("gen_123", response.getResponseHeader("x-generation-id"),
                "header lookup is case-insensitive");
        assertEquals("not-sent-on-success", response.getResponseHeader("Retry-After"));
        assertNull(response.getResponseHeader("X-Not-Sent"));
        assertNull(response.getResponseHeader(null));
        assertTrue(response.getResponseHeaders().containsKey("x-generation-id"),
                "the full header map is exposed too (names as the JDK HttpClient reports them)");
        assertEquals("gen_123", response.getResponseHeaders().get("x-generation-id"));
    }

    @Test
    @DisplayName("a streaming response carries the response headers of the exchange")
    void streamingResponseHeaders() {
        TestApiClient client = new TestApiClient(false);
        AtomicReference<String> data = new AtomicReference<>();

        ApiResponse<?> response = client.execute(streamingRequest("/sse", data::set));

        assertTrue(response.isStreamingResponse());
        assertEquals("hi", data.get(), "the SSE payload was delivered to the handler");
        assertEquals("gen_stream", response.getResponseHeader("X-Generation-Id"));
    }

    // ------------------------------------------------------------------ Retry-After

    @Test
    @DisplayName("retryAfterMs parses delta-seconds and the HTTP-date form and ignores junk")
    void retryAfterParsing() {
        assertEquals(7000L, ApiClient.retryAfterMs(exceptionWithRetryAfter("7")));
        assertEquals(0L, ApiClient.retryAfterMs(exceptionWithRetryAfter("0")));
        assertNull(ApiClient.retryAfterMs(exceptionWithRetryAfter("-5")));
        assertNull(ApiClient.retryAfterMs(exceptionWithRetryAfter("soon")));
        assertNull(ApiClient.retryAfterMs(exceptionWithRetryAfter("")));
        assertNull(ApiClient.retryAfterMs(exceptionWithRetryAfter(null)));

        String future = DateTimeFormatter.RFC_1123_DATE_TIME
                .format(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(120));
        Long futureMs = ApiClient.retryAfterMs(exceptionWithRetryAfter(future));
        assertNotNull(futureMs);
        assertTrue(futureMs > 60_000 && futureMs <= 120_000,
                "a future HTTP-date is honored (got " + futureMs + "ms)");

        String past = DateTimeFormatter.RFC_1123_DATE_TIME
                .format(ZonedDateTime.ofInstant(Instant.now().minusSeconds(120), ZoneOffset.UTC));
        assertEquals(0L, ApiClient.retryAfterMs(exceptionWithRetryAfter(past)),
                "an HTTP-date in the past means retry immediately");

        assertNull(ApiClient.retryAfterMs(new RuntimeException("no context")));
        assertNull(ApiClient.retryAfterMs(null));
    }

    @Test
    @DisplayName("the backoff honors Retry-After only when the retry policy asks for it")
    void honorRetryAfterExtendsBackoff() {
        ApiClient honoring = new TestApiClient(true);
        ApiClient ignoring = new TestApiClient(false);
        RuntimeException ex = exceptionWithRetryAfter("7");

        assertEquals(1000L, ignoring.maybeAdjustSleepForRetryAfter(1000L, ex),
                "off by default: the calculated delay is unchanged");
        assertEquals(7000L, honoring.maybeAdjustSleepForRetryAfter(1000L, ex),
                "the server-provided delay extends a shorter calculated one");
        assertEquals(9000L, honoring.maybeAdjustSleepForRetryAfter(9000L, ex),
                "the server-provided delay never shortens a longer calculated one");
        assertEquals(1000L, honoring.maybeAdjustSleepForRetryAfter(1000L, new RuntimeException("no ctx")),
                "nothing to honor without an error context");
        assertEquals(1000L, honoring.maybeAdjustSleepForRetryAfter(1000L, null));
    }

    @Test
    @DisplayName("of() walks the cause chain to the wrapped transport exception")
    void ofWalksTheCauseChain() {
        RuntimeException inner = exceptionWithRetryAfter("7");

        assertEquals(7000L, ApiClient.retryAfterMs(
                        new ApiClient.StreamingException("Streaming execution failed", inner)),
                "the streaming layers wrap the transport exception in (String, Throwable)");
        assertEquals(7000L, ApiClient.retryAfterMs(
                new java.util.concurrent.CompletionException(inner)));
        assertEquals(7000L, ApiClient.retryAfterMs(
                new ApiClient.StreamingException("outer",
                        new ApiClient.StreamingException("inner", inner))),
                "nested wrappers resolve too");
        assertNull(ApiClient.retryAfterMs(
                new ApiClient.StreamingException("outer", new RuntimeException("no ctx"))));
    }

    @Test
    @DisplayName("a huge Retry-After saturates and is clamped to maxRetryAfterMs")
    void retryAfterIsCapped() {
        ApiClient honoring = new TestApiClient(true);
        RuntimeException huge = exceptionWithRetryAfter("9000000000000000000");

        // overflow-safe parse, then clamped to the default 5-minute ceiling
        assertEquals(300_000L, honoring.maybeAdjustSleepForRetryAfter(1000L, huge));

        ApiClient custom = new TestApiClient(ApiClientSettings.builder()
                .maxRetries(1).initialDelayMs(10).honorRetryAfter(true).maxRetryAfterMs(1_000).build());
        assertEquals(1_000L, custom.maybeAdjustSleepForRetryAfter(100L,
                exceptionWithRetryAfter("3600")), "a smaller ceiling wins");
    }

    @Test
    @DisplayName("the ApiErrorContext toString does not dump the body or header values")
    void toStringDoesNotLeakBody() {
        ApiErrorContext ctx = new ApiErrorContext(429, ERROR_BODY,
                Map.of("Set-Cookie", "session=secret"));
        String text = ctx.toString();
        assertFalse(text.contains("secret"), "header values stay out of logs");
        assertFalse(text.contains("metadata"), "the body stays out of logs");
        assertTrue(text.contains("429"));
    }

    // ------------------------------------------------------------------- test doubles

    private static RuntimeException exceptionWithRetryAfter(String retryAfter) {
        java.util.Map<String, String> headers = retryAfter == null
                ? Map.of()
                : Map.of("Retry-After", retryAfter);
        return new ContextException("test", new ApiErrorContext(429, ERROR_BODY, headers));
    }

    private static TestRequest streamingRequest(String url, java.util.function.Consumer<String> onData) {
        return new TestRequest.Builder()
                .stream(StreamingFormat.SERVER_SENT_EVENTS, new StreamingResponseHandler<String>() {
                    @Override
                    public void onData(String data) {
                        onData.accept(data);
                    }

                    @Override
                    public void onComplete() {
                    }

                    @Override
                    public void onError(Throwable throwable) {
                    }
                })
                .url(url)
                .build();
    }

    /** Exception class with the 2.3.0 error-context constructor. */
    public static class ContextException extends RuntimeException implements ApiErrorContextAware {
        private final ApiErrorContext apiErrorContext;

        public ContextException(String message) {
            super(message);
            this.apiErrorContext = null;
        }

        public ContextException(String message, ApiErrorContext apiErrorContext) {
            super(message);
            this.apiErrorContext = apiErrorContext;
        }

        @Override
        public ApiErrorContext getApiErrorContext() {
            return apiErrorContext;
        }
    }

    /** Exception class as a pre-2.3.0 client library would register it: message only. */
    public static class LegacyException extends RuntimeException {
        public LegacyException(String message) {
            super(message);
        }
    }

    private static class TestApiClient extends ApiClient {
        TestApiClient(boolean honorRetryAfter) {
            this(ApiClientSettings.builder()
                    .maxRetries(1)
                    .initialDelayMs(10)
                    .honorRetryAfter(honorRetryAfter)
                    .build());
        }

        TestApiClient(ApiClientSettings settings) {
            super(settings);
            setBaseUrl("http://127.0.0.1:" + port);
        }

        void register(int statusCode, Class<? extends RuntimeException> clazz, String message, boolean retry) {
            registerStatusCodeException(statusCode, clazz, message, retry);
        }
    }

    private static class TestRequest extends ApiRequest<TestResponse> {
        private final String relativeUrl;

        protected TestRequest(Builder builder) {
            super(builder);
            this.relativeUrl = builder.url;
        }

        TestRequest(String url) {
            this(new Builder().url(url));
        }

        @Override
        public String getRelativeUrl() {
            return relativeUrl;
        }

        @Override
        public String getHttpMethod() {
            return "POST";
        }

        @Override
        public String getContentType() {
            return "application/json";
        }

        @Override
        public String getBody() {
            return "{\"test\": true}";
        }

        @Override
        public byte[] getBodyBytes() {
            return getBody().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public TestResponse createResponse(String responseBody) {
            return new TestResponse(this, responseBody);
        }

        static class Builder extends ApiRequestBuilderBase<Builder, TestRequest> {
            String url = "/ok";

            Builder url(String url) {
                this.url = url;
                return this;
            }

            @Override
            public TestRequest build() {
                return new TestRequest(this);
            }
        }
    }

    private static class TestResponse extends ApiResponse<TestRequest> {
        private final String body;

        TestResponse(TestRequest request, String body) {
            super(request);
            this.body = body;
        }

        String body() {
            return body;
        }
    }
}

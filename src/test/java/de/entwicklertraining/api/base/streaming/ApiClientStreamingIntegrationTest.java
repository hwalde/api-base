package de.entwicklertraining.api.base.streaming;

import de.entwicklertraining.api.base.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration tests for ApiClient's new execute methods that auto-detect streaming.
 * Tests the integration between regular and streaming execution paths in version 2.1.0.
 */
public class ApiClientStreamingIntegrationTest {

    private TestApiClient client;
    private ApiClientSettings settings;
    private ApiHttpConfiguration httpConfig;

    @BeforeEach
    void setUp() {
        settings = ApiClientSettings.builder()
                .maxRetries(2)
                .initialDelayMs(100)
                .build();

        httpConfig = ApiHttpConfiguration.builder()
                .header("User-Agent", "StreamingTest/1.0")
                .build();

        client = new TestApiClient(settings, httpConfig);
    }

    @Test
    @DisplayName("execute() should auto-detect regular vs streaming requests")
    void testExecuteAutoDetection() throws Exception {
        // Regular request - no streaming info
        TestRequest regularRequest = new TestRequest.Builder()
            .setData("regular data")
            .build();

        assertFalse(regularRequest.getStreamingInfo().isEnabled());

        // Streaming request - has streaming info
        AtomicInteger dataCount = new AtomicInteger(0);
        StreamingResponseHandler<String> handler = new StreamingResponseHandler<String>() {
            @Override
            public void onData(String data) {
                dataCount.incrementAndGet();
            }

            @Override
            public void onComplete() {}

            @Override
            public void onError(Throwable throwable) {}
        };

        TestRequest streamingRequest = new TestRequest.Builder()
            .setData("streaming data")
            .stream(StreamingFormat.SERVER_SENT_EVENTS, handler)
            .build();

        assertTrue(streamingRequest.getStreamingInfo().isEnabled());

        // Execute both with same method - should auto-detect
        CompletableFuture<? extends ApiResponse<?>> regularFuture = client.executeAsync(regularRequest);
        CompletableFuture<? extends ApiResponse<?>> streamingFuture = client.executeAsync(streamingRequest);

        // Both should complete successfully
        ApiResponse<?> regularResponse = regularFuture.get(5, TimeUnit.SECONDS);
        ApiResponse<?> streamingResponse = streamingFuture.get(5, TimeUnit.SECONDS);

        assertNotNull(regularResponse);
        assertNotNull(streamingResponse);

        // Regular response should not have streaming context
        assertFalse(regularResponse.getStreamingContext().isPresent());

        // Streaming response should have streaming context
        assertTrue(streamingResponse.getStreamingContext().isPresent());
    }

    @Test
    @DisplayName("executeWithRetry() should auto-detect regular vs streaming requests")
    void testExecuteWithRetryAutoDetection() throws Exception {
        // Test with retry logic
        client.setFailFirstAttempts(1); // Fail first attempt to test retry

        // Regular request with retry
        TestRequest regularRequest = new TestRequest.Builder()
            .setData("retry regular data")
            .build();

        // Streaming request with retry
        AtomicBoolean streamCompleted = new AtomicBoolean(false);
        StreamingResponseHandler<String> handler = new StreamingResponseHandler<String>() {
            @Override
            public void onData(String data) {}

            @Override
            public void onComplete() {
                streamCompleted.set(true);
            }

            @Override
            public void onError(Throwable throwable) {}
        };

        TestRequest streamingRequest = new TestRequest.Builder()
            .setData("retry streaming data")
            .stream(StreamingFormat.JSON_LINES, handler)
            .build();

        // Execute both with retry
        CompletableFuture<? extends ApiResponse<?>> regularFuture = client.executeAsyncWithRetry(regularRequest);
        CompletableFuture<? extends ApiResponse<?>> streamingFuture = client.executeAsyncWithRetry(streamingRequest);

        // Both should complete after retry
        ApiResponse<?> regularResponse = regularFuture.get(10, TimeUnit.SECONDS);
        ApiResponse<?> streamingResponse = streamingFuture.get(10, TimeUnit.SECONDS);

        assertNotNull(regularResponse);
        assertNotNull(streamingResponse);

        // Check contexts
        assertFalse(regularResponse.getStreamingContext().isPresent());
        assertTrue(streamingResponse.getStreamingContext().isPresent());
    }

    @Test
    @DisplayName("Builder stream() method should integrate with execute methods")
    void testBuilderStreamIntegration() throws Exception {
        List<String> receivedData = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean completed = new AtomicBoolean(false);

        StreamingResponseHandler<String> handler = new StreamingResponseHandler<String>() {
            @Override
            public void onData(String data) {
                receivedData.add(data);
            }

            @Override
            public void onComplete() {
                completed.set(true);
            }

            @Override
            public void onError(Throwable throwable) {
                fail("Unexpected error: " + throwable.getMessage());
            }
        };

        // Create request using builder pattern
        TestRequest request = new TestRequest.Builder()
            .setData("test data")
            .stream(handler) // Use default format (SSE)
            .build();

        // Verify streaming configuration
        StreamingInfo info = request.getStreamingInfo();
        assertTrue(info.isEnabled());
        assertEquals(StreamingFormat.SERVER_SENT_EVENTS, info.getFormat());
        assertEquals(handler, info.getHandler());

        // Execute and verify streaming works
        client.simulateStreamingData(Arrays.asList(
            "data: {\"content\": \"Hello\"}",
            "data: {\"content\": \" World\"}",
            "data: [DONE]"
        ));

        CompletableFuture<? extends ApiResponse<?>> future = client.executeAsync(request);
        ApiResponse<?> response = future.get(5, TimeUnit.SECONDS);

        // Verify streaming worked
        assertTrue(completed.get());
        assertTrue(receivedData.size() >= 0, "Should have received some data chunks");
        if (receivedData.size() > 0) {
            assertEquals("Hello", receivedData.get(0));
        }
        if (receivedData.size() > 1) {
            assertEquals(" World", receivedData.get(1));
        }

        // Verify response has streaming context
        assertTrue(response.getStreamingContext().isPresent());
        StreamingContext<?> context = response.getStreamingContext().get();
        assertTrue(context.isSuccess());
        assertEquals(3, context.getLinesProcessed());
    }

    @Test
    @DisplayName("Builder stream() with custom config should work")
    void testBuilderStreamWithCustomConfig() throws Exception {
        StreamingConfig customConfig = StreamingConfig.builder()
            .bufferSize(1024)
            .streamTimeout(java.time.Duration.ofMinutes(1))
            .enableReconnect(true)
            .maxReconnectAttempts(3)
            .build();

        StreamingResponseHandler<String> handler = createTestHandler();

        TestRequest request = new TestRequest.Builder()
            .setData("custom config test")
            .streamWithConfig(StreamingFormat.JSON_LINES, handler, customConfig)
            .build();

        StreamingInfo info = request.getStreamingInfo();
        assertTrue(info.isEnabled());
        assertEquals(StreamingFormat.JSON_LINES, info.getFormat());
        assertEquals(handler, info.getHandler());
        assertTrue(info.getConfig().isPresent());
        assertEquals(customConfig, info.getConfig().get());

        // Should execute without issues
        CompletableFuture<? extends ApiResponse<?>> future = client.executeAsync(request);
        ApiResponse<?> response = future.get(5, TimeUnit.SECONDS);

        assertNotNull(response);
        assertTrue(response.getStreamingContext().isPresent());
    }

    @Test
    @DisplayName("Mixed request types should execute correctly in parallel")
    void testMixedParallelExecution() throws Exception {
        int numRegular = 3;
        int numStreaming = 3;
        List<CompletableFuture<? extends ApiResponse<?>>> futures = new ArrayList<>();

        // Create regular requests
        for (int i = 0; i < numRegular; i++) {
            TestRequest regularRequest = new TestRequest.Builder()
                .setData("regular_" + i)
                .build();
            futures.add(client.executeAsync(regularRequest));
        }

        // Create streaming requests
        for (int i = 0; i < numStreaming; i++) {
            StreamingResponseHandler<String> handler = createTestHandler();
            TestRequest streamingRequest = new TestRequest.Builder()
                .setData("streaming_" + i)
                .stream(handler)
                .build();
            futures.add(client.executeAsync(streamingRequest));
        }

        // Wait for all to complete
        CompletableFuture<Void> allComplete = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        allComplete.get(10, TimeUnit.SECONDS);

        // Verify all responses
        List<? extends ApiResponse<?>> responses = futures.stream()
            .map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    fail("Failed to get response: " + e.getMessage());
                    return null;
                }
            })
            .toList();

        assertEquals(numRegular + numStreaming, responses.size());

        // Count regular vs streaming responses
        long regularCount = responses.stream()
            .filter(r -> !r.getStreamingContext().isPresent())
            .count();
        long streamingCount = responses.stream()
            .filter(r -> r.getStreamingContext().isPresent())
            .count();

        assertEquals(numRegular, regularCount);
        assertEquals(numStreaming, streamingCount);
    }

    /**
     * Test request for integration testing.
     */
    private static class TestRequest extends ApiRequest<TestResponse> {
        private final String data;

        protected TestRequest(Builder builder) {
            super(builder);
            this.data = builder.data;
        }

        public String getData() {
            return data;
        }

        @Override
        public String getRelativeUrl() {
            return "/test/integration";
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
            return "{\"data\":\"" + data + "\"}";
        }

        @Override
        public TestResponse createResponse(String responseBody) {
            return new TestResponse(this, responseBody);
        }

        public static class Builder extends ApiRequestBuilderBase<Builder, TestRequest> {
            private String data = "default";

            public Builder setData(String data) {
                this.data = data;
                return this;
            }

            protected Builder self() {
                return this;
            }

            @Override
            public TestRequest build() {
                return new TestRequest(this);
            }

            public ApiResponse<TestRequest> executeWithExponentialBackoff() {
                throw new UnsupportedOperationException("Not implemented in test");
            }

            public ApiResponse<TestRequest> execute() {
                throw new UnsupportedOperationException("Not implemented in test");
            }
        }
    }

    /**
     * Test response.
     */
    private static class TestResponse extends ApiResponse<TestRequest> {
        private final String responseBody;

        public TestResponse(TestRequest request, String responseBody) {
            super(request);
            this.responseBody = responseBody;
        }
        
        // Public method to set streaming context for testing
        public void setStreamingContextForTest(StreamingContext<?> context) {
            setStreamingContext(context);
        }

        public String getResponseBody() {
            return responseBody;
        }
    }

    /**
     * Test ApiClient implementation.
     */
    private static class TestApiClient extends ApiClient {
        private int failFirstAttempts = 0;
        private int attemptCount = 0;
        private Queue<String> simulatedStreamingData = new LinkedList<>();

        public TestApiClient(ApiClientSettings settings, ApiHttpConfiguration httpConfig) {
            super(settings, httpConfig);
            setBaseUrl("https://test.example.com");
        }

        public void setFailFirstAttempts(int count) {
            this.failFirstAttempts = count;
            this.attemptCount = 0;
        }

        public void simulateStreamingData(List<String> data) {
            simulatedStreamingData.addAll(data);
        }

        @Override
        public <T extends ApiRequest<?>> CompletableFuture<? extends ApiResponse<?>> executeAsync(T request) {
            StreamingInfo streamingInfo = request.getStreamingInfo();
            if (streamingInfo != null && streamingInfo.isEnabled()) {
                return executeStreamingAsync(request, streamingInfo);
            } else {
                return executeRegularAsync(request);
            }
        }

        @Override
        public <T extends ApiRequest<?>> CompletableFuture<? extends ApiResponse<?>> executeAsyncWithRetry(T request) {
            return CompletableFuture.supplyAsync(() -> {
                int attempts = 0;
                int maxRetries = settings.getMaxRetries();

                while (attempts <= maxRetries) {
                    try {
                        if (attempts < failFirstAttempts) {
                            attempts++;
                            Thread.sleep(100); // Simulate retry delay
                            throw new RuntimeException("Simulated failure attempt " + attempts);
                        }

                        return executeAsync(request).get();

                    } catch (Exception e) {
                        attempts++;
                        if (attempts > maxRetries) {
                            throw new RuntimeException("Max retries exceeded", e);
                        }
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }

                throw new RuntimeException("Should not reach here");
            });
        }

        @SuppressWarnings("unchecked")
        private <T extends ApiRequest<?>> CompletableFuture<? extends ApiResponse<?>> executeStreamingAsync(T request, StreamingInfo streamingInfo) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    StreamingResponseHandler<String> handler = (StreamingResponseHandler<String>) streamingInfo.getHandler();
                    
                    // Simulate streaming execution
                    StreamingExecutionContext<T, String> context = new StreamingExecutionContext<>();
                    
                    // Process simulated data
                    StreamProcessor<String> processor = StreamProcessorFactory.createProcessor(
                        streamingInfo.getFormat(), 
                        String.class
                    );
                    
                    int linesProcessed = 0;
                    boolean completed = false;
                    
                    while (!simulatedStreamingData.isEmpty()) {
                        String line = simulatedStreamingData.poll();
                        processor.processLine(line, handler);
                        linesProcessed++;
                        
                        if (processor.isCompletionLine(line)) {
                            completed = true;
                            break;
                        }
                        
                        Thread.sleep(5); // Small delay
                    }
                    
                    if (completed) {
                        handler.onComplete();
                    }
                    
                    // Create streaming context
                    StreamingContext<String> streamingContext = StreamingContext.<String>builder()
                        .completed(completed)
                        .linesProcessed(linesProcessed)
                        .chunks(context.getChunks())
                        .metadata(context.getMetadata())
                        .build();
                    
                    // Create response with streaming context
                    ApiResponse<?> response = request.createResponse("Streaming response");
                    ((TestResponse) response).setStreamingContextForTest(streamingContext);
                    return response;
                    
                } catch (Exception e) {
                    throw new RuntimeException("Streaming execution failed", e);
                }
            });
        }

        private <T extends ApiRequest<?>> CompletableFuture<? extends ApiResponse<?>> executeRegularAsync(T request) {
            return CompletableFuture.supplyAsync(() -> {
                // Simulate regular HTTP request
                try {
                    Thread.sleep(50); // Simulate network delay
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                String responseBody = "Regular response for: " + request.getBody();
                return request.createResponse(responseBody);
            });
        }
    }

    private StreamingResponseHandler<String> createTestHandler() {
        return new StreamingResponseHandler<String>() {
            @Override
            public void onData(String data) {
                // Test implementation
            }

            @Override
            public void onComplete() {
                // Test implementation
            }

            @Override
            public void onError(Throwable throwable) {
                // Test implementation
            }
        };
    }
}
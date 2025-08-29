package de.entwicklertraining.api.base;

import de.entwicklertraining.cancellation.CancellationException;
import de.entwicklertraining.cancellation.CancellationToken;
import de.entwicklertraining.cancellation.CancellationTokenSource;
import de.entwicklertraining.api.base.streaming.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CancellationToken functionality in the API client,
 * including compatibility with the new streaming architecture in version 2.1.0.
 */
class CancellationTokenIntegrationTest {

    private TestApiClient testClient;
    private ApiClientSettings settings;

    @BeforeEach
    void setUp() {
        settings = ApiClientSettings.builder()
            .maxRetries(3)
            .initialDelayMs(100)
            .build();
        testClient = new TestApiClient(settings);
    }

    @Test
    void testCancellationTokenInBuilder() {
        // Create a cancellation token
        CancellationTokenSource source = CancellationTokenSource.create();
        CancellationToken token = source.getToken();

        // Create a request with cancellation token
        TestRequest request = new TestRequest.Builder()
            .setCancelToken(token)
            .build();

        // Verify token is set
        assertTrue(request.getCancellationToken().isPresent());
        assertEquals(token, request.getCancellationToken().get());
        assertFalse(request.getIsCanceledSupplier().get());

        // Cancel the token
        source.cancel();

        // Verify cancellation is reflected in request
        assertTrue(request.getIsCanceledSupplier().get());
        assertTrue(request.getCancellationToken().get().isCancelled());
    }

    @Test
    void testCancellationTokenFromSupplier() {
        // Create a token from a custom supplier
        boolean[] cancelled = {false};
        Supplier<Boolean> cancelSupplier = () -> cancelled[0];
        CancellationToken token = CancellationToken.fromSupplier(cancelSupplier);

        TestRequest request = new TestRequest.Builder()
            .setCancelToken(token)
            .build();

        assertFalse(request.getIsCanceledSupplier().get());

        // Trigger cancellation
        cancelled[0] = true;

        assertTrue(request.getIsCanceledSupplier().get());
    }

    @Test
    void testCancellationTokenFromCompletableFuture() {
        // Create a token from CompletableFuture
        CompletableFuture<String> future = new CompletableFuture<>();
        CancellationToken token = CancellationToken.fromCompletableFuture(future);

        TestRequest request = new TestRequest.Builder()
            .setCancelToken(token)
            .build();

        assertFalse(request.getIsCanceledSupplier().get());

        // Cancel the future
        future.cancel(true);

        assertTrue(request.getIsCanceledSupplier().get());
    }

    @Test
    void testDirectCancellationTokenCancel() {
        // Create a mutable token via source
        CancellationTokenSource source = CancellationTokenSource.create();
        CancellationToken token = source.getToken();

        TestRequest request = new TestRequest.Builder()
            .setCancelToken(token)
            .build();

        assertFalse(request.getIsCanceledSupplier().get());

        // Cancel directly via token
        token.cancel();

        assertTrue(request.getIsCanceledSupplier().get());
        assertTrue(token.isCancelled());
    }

    @Test
    void testTimeoutCancellationToken() throws InterruptedException {
        // Create a token with timeout
        CancellationTokenSource source = CancellationTokenSource.create(Duration.ofMillis(50));
        CancellationToken token = source.getToken();

        TestRequest request = new TestRequest.Builder()
            .setCancelToken(token)
            .build();

        assertFalse(request.getIsCanceledSupplier().get());

        // Wait for timeout
        Thread.sleep(100);

        assertTrue(request.getIsCanceledSupplier().get());
        assertTrue(token.isCancelled());
    }

    @Test
    void testCancellationTokenWithCancelFuture() {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(1000);
                return "result";
            } catch (InterruptedException e) {
                return "interrupted";
            }
        });

        TestRequest request = new TestRequest.Builder()
            .setCancelFuture(future)
            .build();

        assertFalse(request.getIsCanceledSupplier().get());

        // Cancel the future
        future.cancel(true);

        assertTrue(request.getIsCanceledSupplier().get());
    }

    @Test
    void testRequestCancelMethodStillWorks() {
        // Test that the original cancel() method still works alongside tokens
        TestRequest request = new TestRequest.Builder().build();

        assertFalse(request.getIsCanceledSupplier().get());

        // Cancel using the original request method
        request.cancel();

        assertTrue(request.getIsCanceledSupplier().get());
    }

    @Test
    void testCombinedCancellation() {
        // Test combining token cancellation with request cancellation
        CancellationTokenSource source = CancellationTokenSource.create();
        CancellationToken token = source.getToken();

        TestRequest request = new TestRequest.Builder()
            .setCancelToken(token)
            .build();

        assertFalse(request.getIsCanceledSupplier().get());

        // Cancel the request directly (should trigger regardless of token state)
        request.cancel();

        assertTrue(request.getIsCanceledSupplier().get());

        // Token should still be uncancelled
        assertFalse(token.isCancelled());
    }

    @Test
    void testCancellationTokenThrowIfCancelled() {
        // Test that CancellationToken properly throws CancellationException
        CancellationTokenSource source = CancellationTokenSource.create();
        CancellationToken token = source.getToken();

        // Should not throw when not cancelled
        assertDoesNotThrow(() -> token.throwIfCancelled());
        assertDoesNotThrow(() -> token.throwIfCancelled("Custom message"));

        // Cancel the token
        source.cancel();

        // Should throw CancellationException when cancelled
        assertThrows(CancellationException.class, token::throwIfCancelled);
        assertThrows(CancellationException.class, () -> token.throwIfCancelled("Custom message"));

        // Test that we can catch it appropriately
        try {
            token.throwIfCancelled("Operation cancelled gracefully");
            fail("Should have thrown CancellationException");
        } catch (CancellationException e) {
            assertEquals("Operation cancelled gracefully", e.getMessage());
        }
    }

    @Test
    void testGracefulCancellationPattern() {
        // Test the typical graceful cancellation pattern
        CancellationTokenSource source = CancellationTokenSource.create();
        CancellationToken token = source.getToken();

        // Simulate a long-running operation with graceful cancellation
        boolean operationCompleted = false;
        boolean operationCancelled = false;

        try {
            for (int i = 0; i < 10; i++) {
                // Check for cancellation at each iteration
                token.throwIfCancelled("Operation cancelled gracefully at iteration " + i);

                if (i == 5) {
                    // Cancel after 5 iterations
                    source.cancel();
                }

                // Simulate some work
                Thread.sleep(10);
            }
            operationCompleted = true;
        } catch (CancellationException e) {
            operationCancelled = true;
            // Message content may vary between implementations
            assertNotNull(e.getMessage());
        } catch (InterruptedException e) {
            fail("Should not have been interrupted");
        }

        assertFalse(operationCompleted);
        assertTrue(operationCancelled);
        assertTrue(token.isCancelled());
    }
    
    @Test
    void testCancellationWithStreamingRequest() throws InterruptedException {
        // Test cancellation with the new streaming architecture
        CancellationTokenSource source = CancellationTokenSource.create();
        CancellationToken token = source.getToken();
        
        AtomicBoolean streamStarted = new AtomicBoolean(false);
        AtomicBoolean streamCancelled = new AtomicBoolean(false);
        AtomicInteger chunksReceived = new AtomicInteger(0);
        
        StreamingResponseHandler<String> handler = new StreamingResponseHandler<String>() {
            @Override
            public void onStreamStart() {
                streamStarted.set(true);
            }
            
            @Override
            public void onData(String data) {
                chunksReceived.incrementAndGet();
                // Check for cancellation during data processing
                try {
                    token.throwIfCancelled("Stream cancelled during data processing");
                } catch (CancellationException e) {
                    streamCancelled.set(true);
                    throw e;
                }
            }
            
            @Override
            public void onComplete() {
                // Stream completed normally
            }
            
            @Override
            public void onError(Throwable throwable) {
                if (throwable instanceof CancellationException) {
                    streamCancelled.set(true);
                }
            }
        };
        
        // Create streaming request with cancellation token
        TestRequest request = new TestRequest.Builder()
            .setCancelToken(token)
            .stream(StreamingFormat.SERVER_SENT_EVENTS, handler)
            .build();
        
        // Verify both streaming and cancellation are configured
        assertTrue(request.getStreamingInfo().isEnabled());
        assertTrue(request.getCancellationToken().isPresent());
        assertEquals(token, request.getCancellationToken().get());
        
        // Start processing (simulate some chunks, then cancel)
        new Thread(() -> {
            try {
                Thread.sleep(50); // Let stream start
                if (streamStarted.get()) {
                    // Simulate receiving a few chunks
                    handler.onData("chunk1");
                    Thread.sleep(10);
                    handler.onData("chunk2");
                    Thread.sleep(10);
                    
                    // Cancel the token
                    source.cancel();
                    
                    // Try to send more data (should be cancelled)
                    try {
                        handler.onData("chunk3");
                    } catch (CancellationException e) {
                        // Expected
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        // Start the stream
        handler.onStreamStart();
        
        // Wait for cancellation to take effect
        Thread.sleep(200);
        
        // Verify cancellation worked
        assertTrue(streamStarted.get(), "Stream should have started");
        assertTrue(token.isCancelled(), "Token should be cancelled");
        assertTrue(streamCancelled.get(), "Stream should have been cancelled");
        assertTrue(chunksReceived.get() >= 2, "Should have received at least 2 chunks before cancellation");
    }
    
    @Test
    void testCancellationCompatibilityBetweenRegularAndStreaming() {
        // Test that cancellation works consistently between regular and streaming requests
        CancellationTokenSource source1 = CancellationTokenSource.create();
        CancellationTokenSource source2 = CancellationTokenSource.create();
        
        // Regular request with cancellation
        TestRequest regularRequest = new TestRequest.Builder()
            .setCancelToken(source1.getToken())
            .build();
            
        assertFalse(regularRequest.getStreamingInfo().isEnabled());
        assertTrue(regularRequest.getCancellationToken().isPresent());
        
        // Streaming request with cancellation
        StreamingResponseHandler<String> handler = new StreamingResponseHandler<String>() {
            @Override
            public void onData(String data) {}
            @Override
            public void onComplete() {}
            @Override
            public void onError(Throwable throwable) {}
        };
        
        TestRequest streamingRequest = new TestRequest.Builder()
            .setCancelToken(source2.getToken())
            .stream(handler)
            .build();
            
        assertTrue(streamingRequest.getStreamingInfo().isEnabled());
        assertTrue(streamingRequest.getCancellationToken().isPresent());
        
        // Both should respond to cancellation in the same way
        assertFalse(regularRequest.getIsCanceledSupplier().get());
        assertFalse(streamingRequest.getIsCanceledSupplier().get());
        
        // Cancel both
        source1.cancel();
        source2.cancel();
        
        assertTrue(regularRequest.getIsCanceledSupplier().get());
        assertTrue(streamingRequest.getIsCanceledSupplier().get());
        
        // Both tokens should throw when checked
        assertThrows(CancellationException.class, () -> source1.getToken().throwIfCancelled());
        assertThrows(CancellationException.class, () -> source2.getToken().throwIfCancelled());
    }

    /**
     * Simple test API client for testing.
     */
    private static class TestApiClient extends ApiClient {
        public TestApiClient(ApiClientSettings settings) {
            super(settings);
            setBaseUrl("https://httpbin.org");
        }
    }

    /**
     * Test request for testing both regular and streaming scenarios with cancellation.
     */
    private static class TestRequest extends ApiRequest<TestResponse> {
        
        protected TestRequest(Builder builder) {
            super(builder);
        }

        @Override
        public String getRelativeUrl() {
            return "/test";
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
            return getBody().getBytes();
        }

        @Override
        public TestResponse createResponse(String responseBody) {
            return new TestResponse(this, responseBody);
        }

        public static class Builder extends ApiRequestBuilderBase<Builder, TestRequest> {
            protected Builder self() { return this; }
            
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
     * Simple test response for testing.
     */
    private static class TestResponse extends ApiResponse<TestRequest> {
        private final String body;

        public TestResponse(TestRequest request, String body) {
            super(request);
            this.body = body;
        }

        public String getBody() {
            return body;
        }
    }
}
package de.entwicklertraining.api.base.streaming;

import de.entwicklertraining.api.base.ApiClient;
import de.entwicklertraining.api.base.ApiClientSettings;
import de.entwicklertraining.api.base.ApiResponse;
import de.entwicklertraining.api.base.ApiRequest;
import de.entwicklertraining.api.base.ApiRequestBuilderBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive test suite for streaming functionality in version 2.1.0.
 * 
 * This test class focuses on testing the new streaming architecture components
 * including StreamingInfo, StreamingContext, and StreamingExecutionContext.
 */
public class ComprehensiveStreamingTest {
    
    @Test
    @DisplayName("StreamingFormat enum should have all expected values")
    void testStreamingFormats() {
        // Test that all expected streaming formats exist
        assertNotNull(StreamingFormat.SERVER_SENT_EVENTS);
        assertNotNull(StreamingFormat.JSON_LINES);
        assertNotNull(StreamingFormat.CUSTOM);
        
        // Test format properties
        assertEquals("text/event-stream", StreamingFormat.SERVER_SENT_EVENTS.getAcceptHeader());
        assertEquals("application/x-ndjson", StreamingFormat.JSON_LINES.getAcceptHeader());
        assertEquals("application/octet-stream", StreamingFormat.CUSTOM.getAcceptHeader());
    }
    
    @Test
    @DisplayName("StreamingConfig builder should work correctly")
    void testStreamingConfigBuilder() {
        // Test default builder
        StreamingConfig defaultConfig = StreamingConfig.builder().build();
        assertNotNull(defaultConfig);
        assertTrue(defaultConfig.getBufferSize() > 0);
        
        // Test custom configuration
        StreamingConfig customConfig = StreamingConfig.builder()
                .bufferSize(4096)
                .streamTimeout(java.time.Duration.ofMinutes(2))
                .enableReconnect(true)
                .maxReconnectAttempts(5)
                .build();
        
        assertEquals(4096, customConfig.getBufferSize());
        assertEquals(java.time.Duration.ofMinutes(2), customConfig.getStreamTimeout());
        assertTrue(customConfig.isReconnectEnabled());
        assertEquals(5, customConfig.getMaxReconnectAttempts());
    }
    
    @Test
    @DisplayName("StreamingConfig should validate input parameters")
    void testStreamingConfigValidation() {
        // Test invalid buffer size
        assertThrows(IllegalArgumentException.class, () -> 
                StreamingConfig.builder().bufferSize(-1).build());
        
        assertThrows(IllegalArgumentException.class, () -> 
                StreamingConfig.builder().bufferSize(0).build());
        
        // Test invalid timeout
        assertThrows(IllegalArgumentException.class, () -> 
                StreamingConfig.builder()
                    .streamTimeout(java.time.Duration.ofSeconds(-1))
                    .build());
    }
    
    @Test
    @DisplayName("StreamProcessorFactory should create correct processors")
    void testStreamProcessorFactory() {
        // Test SSE processor creation
        StreamProcessor<String> sseProcessor = StreamProcessorFactory
                .createProcessor(StreamingFormat.SERVER_SENT_EVENTS, String.class);
        
        assertNotNull(sseProcessor);
        assertEquals(StreamingFormat.SERVER_SENT_EVENTS, sseProcessor.getFormat());
        
        // Test JSON Lines processor creation
        StreamProcessor<String> jsonProcessor = StreamProcessorFactory
                .createProcessor(StreamingFormat.JSON_LINES, String.class);
        
        assertNotNull(jsonProcessor);
        assertEquals(StreamingFormat.JSON_LINES, jsonProcessor.getFormat());
        
        // Test unsupported format
        assertThrows(IllegalArgumentException.class, () -> 
                StreamProcessorFactory.createProcessor(StreamingFormat.CUSTOM, String.class));
    }
    
    @Test
    @DisplayName("SSE Stream Processor should parse Server-Sent Events correctly")
    void testSSEStreamProcessorBasic() throws Exception {
        // Create SSE processor
        SSEStreamProcessor<String> processor = new SSEStreamProcessor<>(
                String.class, 
                SSEStreamProcessor.CommonExtractors.CONTENT_FIELD
        );
        
        // Collect results
        List<String> receivedData = new ArrayList<>();
        Map<String, Object> receivedMetadata = new HashMap<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicBoolean errored = new AtomicBoolean(false);
        
        // Create test handler
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
                errored.set(true);
            }
            
            @Override
            public void onMetadata(Map<String, Object> metadata) {
                receivedMetadata.putAll(metadata);
            }
        };
        
        // Process SSE data
        processor.processLine("event: message", handler);
        processor.processLine("id: 123", handler);
        processor.processLine("data: {\"content\": \"Hello World\"}", handler);
        processor.processLine("", handler); // End of event
        processor.processLine("data: [DONE]", handler); // Completion signal
        
        // Verify results
        assertEquals(1, receivedData.size(), "Should have received one data chunk");
        assertEquals("Hello World", receivedData.get(0), "Should extract content field correctly");
        assertTrue(completed.get(), "Should have completed");
        assertFalse(errored.get(), "Should not have errored");
        
        // Verify metadata
        assertTrue(receivedMetadata.containsKey("event_type"));
        assertEquals("message", receivedMetadata.get("event_type"));
        assertTrue(receivedMetadata.containsKey("event_id"));
        assertEquals("123", receivedMetadata.get("event_id"));
    }
    
    @Test
    @DisplayName("JSON Lines Stream Processor should parse JSON Lines correctly")
    void testJsonLinesStreamProcessor() throws Exception {
        // Create JSON Lines processor
        JsonLinesStreamProcessor<String> processor = new JsonLinesStreamProcessor<>(
                String.class,
                JsonLinesStreamProcessor.CommonExtractors.CONTENT_FIELD
        );
        
        // Collect results
        List<String> receivedData = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicBoolean errored = new AtomicBoolean(false);
        
        // Create test handler
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
                errored.set(true);
            }
        };
        
        // Process JSON Lines data
        processor.processLine("{\"type\": \"chunk\", \"content\": \"Hello\"}", handler);
        processor.processLine("{\"type\": \"chunk\", \"content\": \" World\"}", handler);
        processor.processLine("{\"type\": \"done\"}", handler);
        
        // Verify results
        assertEquals(2, receivedData.size(), "Should have received two data chunks");
        assertEquals("Hello", receivedData.get(0), "First chunk should be 'Hello'");
        assertEquals(" World", receivedData.get(1), "Second chunk should be ' World'");
        assertTrue(completed.get(), "Should have completed");
        assertFalse(errored.get(), "Should not have errored");
    }
    
    @Test
    @DisplayName("StreamingInfo should create correctly with different configurations")
    void testStreamingInfoCreation() {
        // Test non-streaming info
        StreamingInfo nonStreaming = new StreamingInfo();
        assertFalse(nonStreaming.isEnabled());
        assertNull(nonStreaming.getFormat());
        assertNull(nonStreaming.getHandler());
        assertFalse(nonStreaming.getConfig().isPresent());
        
        // Test streaming info with default config
        StreamingResponseHandler<String> handler = createTestHandler();
        StreamingInfo streaming = new StreamingInfo(StreamingFormat.SERVER_SENT_EVENTS, handler);
        assertTrue(streaming.isEnabled());
        assertEquals(StreamingFormat.SERVER_SENT_EVENTS, streaming.getFormat());
        assertEquals(handler, streaming.getHandler());
        assertNotNull(streaming.getConfigOrDefault());
        
        // Test streaming info with custom config
        StreamingConfig customConfig = StreamingConfig.builder()
            .bufferSize(4096)
            .build();
        StreamingInfo customStreaming = new StreamingInfo(StreamingFormat.JSON_LINES, handler, customConfig);
        assertTrue(customStreaming.isEnabled());
        assertEquals(StreamingFormat.JSON_LINES, customStreaming.getFormat());
        assertEquals(handler, customStreaming.getHandler());
        assertEquals(customConfig, customStreaming.getConfig().get());
    }
    
    @Test
    @DisplayName("StreamingInfo builder should work correctly")
    void testStreamingInfoBuilder() {
        StreamingResponseHandler<String> handler = createTestHandler();
        
        // Test builder with format and handler
        StreamingInfo info = StreamingInfo.builder()
            .format(StreamingFormat.SERVER_SENT_EVENTS)
            .handler(handler)
            .build();
            
        assertTrue(info.isEnabled());
        assertEquals(StreamingFormat.SERVER_SENT_EVENTS, info.getFormat());
        assertEquals(handler, info.getHandler());
        
        // Test disabled builder
        StreamingInfo disabled = StreamingInfo.builder()
            .disabled()
            .build();
            
        assertFalse(disabled.isEnabled());
        assertNull(disabled.getFormat());
        assertNull(disabled.getHandler());
        
        // Test builder with config
        StreamingConfig config = StreamingConfig.builder()
            .bufferSize(4096)
            .build();
            
        StreamingInfo withConfig = StreamingInfo.builder()
            .format(StreamingFormat.JSON_LINES)
            .handler(handler)
            .config(config)
            .build();
            
        assertTrue(withConfig.isEnabled());
        assertEquals(StreamingFormat.JSON_LINES, withConfig.getFormat());
        assertEquals(handler, withConfig.getHandler());
        assertEquals(config, withConfig.getConfig().get());
    }
    
    @Test
    @DisplayName("StreamingContext should collect data and metadata correctly")
    void testStreamingContextDataCollection() {
        // Create streaming context using builder
        List<String> chunks = Arrays.asList("Hello", " ", "World");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tokens", 42);
        metadata.put("processing_time", 1.5);
        
        long startTime = System.currentTimeMillis();
        long endTime = startTime + 1000;
        
        StreamingContext<String> context = StreamingContext.<String>builder()
            .completed(true)
            .linesProcessed(5)
            .chunks(chunks)
            .metadata(metadata)
            .startTime(startTime)
            .endTime(endTime)
            .build();
        
        // Test results
        assertTrue(context.isSuccess());
        assertTrue(context.isCompleted());
        assertFalse(context.isCanceled());
        assertFalse(context.getError().isPresent());
        assertEquals(5, context.getLinesProcessed());
        assertEquals(3, context.getChunks().size());
        assertEquals("Hello", context.getChunks().get(0));
        assertEquals(" ", context.getChunks().get(1));
        assertEquals("World", context.getChunks().get(2));
        assertEquals(42, context.getMetadata().get("tokens"));
        assertEquals(1.5, context.getMetadata().get("processing_time"));
        assertEquals(1000, context.getDurationMillis());
        
        // Test typed metadata access
        Optional<Integer> tokens = context.getMetadataValue("tokens", Integer.class);
        assertTrue(tokens.isPresent());
        assertEquals(42, tokens.get().intValue());
    }
    
    @Test
    @DisplayName("StreamingContext should handle errors correctly")
    void testStreamingContextErrorHandling() {
        // Create context with error
        RuntimeException error = new RuntimeException("Test error");
        List<String> partialChunks = Arrays.asList("Some data");
        
        StreamingContext<String> errorContext = StreamingContext.<String>builder()
            .completed(false)
            .canceled(false)
            .error(error)
            .linesProcessed(1)
            .chunks(partialChunks)
            .build();
        
        // Test error state
        assertFalse(errorContext.isSuccess());
        assertFalse(errorContext.isCompleted());
        assertFalse(errorContext.isCanceled());
        assertTrue(errorContext.getError().isPresent());
        assertEquals(error, errorContext.getError().get());
        assertEquals(1, errorContext.getLinesProcessed());
        assertEquals(1, errorContext.getChunks().size());
        assertEquals("Some data", errorContext.getChunks().get(0));
        
        // Test canceled context
        StreamingContext<String> canceledContext = StreamingContext.<String>builder()
            .completed(false)
            .canceled(true)
            .linesProcessed(2)
            .build();
        
        assertFalse(canceledContext.isSuccess());
        assertFalse(canceledContext.isCompleted());
        assertTrue(canceledContext.isCanceled());
        assertFalse(canceledContext.getError().isPresent());
    }
    
    @Test
    @DisplayName("StreamingExecutionContext should collect data properly")
    void testStreamingExecutionContext() {
        // Test StreamingExecutionContext
        StreamingExecutionContext<TestRequest, String> context = new StreamingExecutionContext<>();
        
        // Test initial state
        assertTrue(context.getChunks().isEmpty());
        assertTrue(context.getMetadata().isEmpty());
        assertNull(context.getResponseBody());
        assertNull(context.getResponseBytes());
        
        // Add data
        context.addChunk("chunk1");
        context.addChunk("chunk2");
        context.addMetadata("key1", "value1");
        context.addMetadata("key2", 42);
        context.setResponseBody("response body");
        context.setResponseBytes("response bytes".getBytes());
        
        // Verify data
        assertEquals(2, context.getChunks().size());
        assertEquals("chunk1", context.getChunks().get(0));
        assertEquals("chunk2", context.getChunks().get(1));
        assertEquals(2, context.getMetadata().size());
        assertEquals("value1", context.getMetadata().get("key1"));
        assertEquals(42, context.getMetadata().get("key2"));
        assertEquals("response body", context.getResponseBody());
        assertArrayEquals("response bytes".getBytes(), context.getResponseBytes());
        
        // Test clear
        context.clear();
        assertTrue(context.getChunks().isEmpty());
        assertTrue(context.getMetadata().isEmpty());
        assertNull(context.getResponseBody());
        assertNull(context.getResponseBytes());
    }
    
    @Test
    @DisplayName("Builder stream() method should configure StreamingInfo correctly")
    void testBuilderStreamMethod() {
        StreamingResponseHandler<String> handler = createTestHandler();
        
        // Test stream() with default format
        TestRequest.Builder builder1 = new TestRequest.Builder();
        builder1.stream(handler);
        TestRequest request1 = builder1.build();
        
        StreamingInfo info1 = request1.getStreamingInfo();
        assertNotNull(info1);
        assertTrue(info1.isEnabled());
        assertEquals(StreamingFormat.SERVER_SENT_EVENTS, info1.getFormat());
        assertEquals(handler, info1.getHandler());
        
        // Test stream() with specific format
        TestRequest.Builder builder2 = new TestRequest.Builder();
        builder2.stream(StreamingFormat.JSON_LINES, handler);
        TestRequest request2 = builder2.build();
        
        StreamingInfo info2 = request2.getStreamingInfo();
        assertNotNull(info2);
        assertTrue(info2.isEnabled());
        assertEquals(StreamingFormat.JSON_LINES, info2.getFormat());
        assertEquals(handler, info2.getHandler());
        
        // Test stream() with format and config
        StreamingConfig config = StreamingConfig.builder()
            .bufferSize(2048)
            .build();
            
        TestRequest.Builder builder3 = new TestRequest.Builder();
        builder3.stream(StreamingFormat.SERVER_SENT_EVENTS, handler, config);
        TestRequest request3 = builder3.build();
        
        StreamingInfo info3 = request3.getStreamingInfo();
        assertNotNull(info3);
        assertTrue(info3.isEnabled());
        assertEquals(StreamingFormat.SERVER_SENT_EVENTS, info3.getFormat());
        assertEquals(handler, info3.getHandler());
        assertEquals(config, info3.getConfig().get());
    }
    
    /**
     * Test request implementation for the new architecture.
     */
    private static class TestRequest extends ApiRequest<TestResponse> {
        
        public TestRequest(ApiRequestBuilderBase<?, TestRequest> builder) {
            super(builder);
        }
        
        @Override
        public String getRelativeUrl() { return "/test/stream"; }
        
        @Override
        public String getHttpMethod() { return "POST"; }
        
        @Override
        public String getBody() { return "{\"test\": true}"; }
        
        @Override
        public TestResponse createResponse(String responseBody) {
            return new TestResponse(this, responseBody);
        }
        
        public static class Builder extends ApiRequestBuilderBase<Builder, TestRequest> {
            @Override
            protected Builder self() { return this; }
            
            @Override
            public TestRequest build() {
                return new TestRequest(this);
            }
            
            @Override
            public ApiResponse<TestRequest> executeWithExponentialBackoff() {
                throw new UnsupportedOperationException("Not implemented in test");
            }
            
            @Override
            public ApiResponse<TestRequest> execute() {
                throw new UnsupportedOperationException("Not implemented in test");
            }
        }
    }
    
    /**
     * Test response for the new architecture.
     */
    private static class TestResponse extends ApiResponse<TestRequest> {
        private final String responseBody;
        
        public TestResponse(TestRequest request, String responseBody) {
            super(request);
            this.responseBody = responseBody;
        }
        
        public String getResponseBody() {
            return responseBody;
        }
    }
    
    /**
     * Helper method to create a test streaming response handler.
     */
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
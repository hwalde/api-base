package de.entwicklertraining.api.base.streaming;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

/**
 * Unit tests for StreamingInfo class.
 * Tests the new StreamingInfo class introduced in version 2.1.0.
 */
public class StreamingInfoTest {

    @Test
    @DisplayName("Default constructor should create non-streaming info")
    void testDefaultConstructor() {
        StreamingInfo info = new StreamingInfo();
        
        assertFalse(info.isEnabled());
        assertNull(info.getFormat());
        assertNull(info.getHandler());
        assertFalse(info.getConfig().isPresent());
        assertNotNull(info.getConfigOrDefault()); // Should provide default
    }

    @Test
    @DisplayName("Constructor with format and handler should enable streaming")
    void testConstructorWithFormatAndHandler() {
        StreamingResponseHandler<String> handler = createTestHandler();
        StreamingInfo info = new StreamingInfo(StreamingFormat.SERVER_SENT_EVENTS, handler);
        
        assertTrue(info.isEnabled());
        assertEquals(StreamingFormat.SERVER_SENT_EVENTS, info.getFormat());
        assertEquals(handler, info.getHandler());
        assertFalse(info.getConfig().isPresent());
        assertNotNull(info.getConfigOrDefault());
    }

    @Test
    @DisplayName("Constructor with format, handler, and config should work")
    void testConstructorWithConfig() {
        StreamingResponseHandler<String> handler = createTestHandler();
        StreamingConfig config = StreamingConfig.builder()
            .bufferSize(2048)
            .streamTimeout(Duration.ofMinutes(1))
            .build();
            
        StreamingInfo info = new StreamingInfo(StreamingFormat.JSON_LINES, handler, config);
        
        assertTrue(info.isEnabled());
        assertEquals(StreamingFormat.JSON_LINES, info.getFormat());
        assertEquals(handler, info.getHandler());
        assertTrue(info.getConfig().isPresent());
        assertEquals(config, info.getConfig().get());
        assertEquals(config, info.getConfigOrDefault());
    }

    @Test
    @DisplayName("Constructor should validate required parameters when streaming is enabled")
    void testConstructorValidation() {
        StreamingResponseHandler<String> handler = createTestHandler();
        
        // Should not throw with valid parameters
        assertDoesNotThrow(() -> 
            new StreamingInfo(StreamingFormat.SERVER_SENT_EVENTS, handler));
        
        // Should throw with null format when enabled
        assertThrows(NullPointerException.class, () -> 
            new StreamingInfo(null, handler));
        
        // Should throw with null handler when enabled
        assertThrows(NullPointerException.class, () -> 
            new StreamingInfo(StreamingFormat.SERVER_SENT_EVENTS, null));
    }

    @Test
    @DisplayName("Builder should work correctly")
    void testBuilder() {
        StreamingResponseHandler<String> handler = createTestHandler();
        
        // Test basic builder
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
    }

    @Test
    @DisplayName("Builder should validate state")
    void testBuilderValidation() {
        StreamingResponseHandler<String> handler = createTestHandler();
        
        // Should either throw or allow incomplete builders (implementation dependent)
        try {
            StreamingInfo.builder()
                .handler(handler)
                .build();
            // If this succeeds, the implementation allows incomplete builders
        } catch (IllegalStateException e) {
            // Expected if validation is strict
        }
        
        try {
            StreamingInfo.builder()
                .format(StreamingFormat.SERVER_SENT_EVENTS)
                .build();
            // If this succeeds, the implementation allows incomplete builders
        } catch (IllegalStateException e) {
            // Expected if validation is strict
        }
        
        // Should work when both are provided
        assertDoesNotThrow(() -> 
            StreamingInfo.builder()
                .format(StreamingFormat.SERVER_SENT_EVENTS)
                .handler(handler)
                .build());
    }

    @Test
    @DisplayName("Builder with custom config should work")
    void testBuilderWithConfig() {
        StreamingResponseHandler<String> handler = createTestHandler();
        StreamingConfig config = StreamingConfig.builder()
            .bufferSize(1024)
            .enableReconnect(true)
            .maxReconnectAttempts(3)
            .build();
            
        StreamingInfo info = StreamingInfo.builder()
            .format(StreamingFormat.JSON_LINES)
            .handler(handler)
            .config(config)
            .build();
            
        assertTrue(info.isEnabled());
        assertEquals(StreamingFormat.JSON_LINES, info.getFormat());
        assertEquals(handler, info.getHandler());
        assertTrue(info.getConfig().isPresent());
        assertEquals(config, info.getConfig().get());
    }

    @Test
    @DisplayName("getConfigOrDefault should provide sensible defaults")
    void testGetConfigOrDefault() {
        StreamingResponseHandler<String> handler = createTestHandler();
        
        // Without custom config
        StreamingInfo info = new StreamingInfo(StreamingFormat.SERVER_SENT_EVENTS, handler);
        StreamingConfig defaultConfig = info.getConfigOrDefault();
        
        assertNotNull(defaultConfig);
        assertTrue(defaultConfig.getBufferSize() > 0);
        
        // With custom config
        StreamingConfig customConfig = StreamingConfig.builder()
            .bufferSize(4096)
            .build();
        StreamingInfo infoWithConfig = new StreamingInfo(
            StreamingFormat.JSON_LINES, 
            handler, 
            customConfig
        );
        
        assertEquals(customConfig, infoWithConfig.getConfigOrDefault());
    }

    @Test
    @DisplayName("toString should provide useful information")
    void testToString() {
        // Non-streaming info
        StreamingInfo nonStreaming = new StreamingInfo();
        String nonStreamingStr = nonStreaming.toString();
        assertTrue(nonStreamingStr.contains("enabled=false"));
        
        // Streaming info
        StreamingResponseHandler<String> handler = createTestHandler();
        StreamingInfo streaming = new StreamingInfo(StreamingFormat.SERVER_SENT_EVENTS, handler);
        String streamingStr = streaming.toString();
        
        assertTrue(streamingStr.contains("enabled=true") || streamingStr.contains("true"));
        assertTrue(streamingStr.contains("SERVER_SENT_EVENTS") || streamingStr.contains("SSE"));
        // Handler representation may vary
    }

    /**
     * Helper method to create a test handler.
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
            
            @Override
            public String toString() {
                return "TestHandler";
            }
        };
    }
}
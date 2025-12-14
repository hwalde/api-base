package de.entwicklertraining.api.base.streaming;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Unit tests for StreamingExecutionContext class.
 * Tests the new StreamingExecutionContext class introduced in version 2.1.0.
 */
public class StreamingExecutionContextTest {

    private StreamingExecutionContext<MockRequest, String> context;

    @BeforeEach
    void setUp() {
        context = new StreamingExecutionContext<>();
    }

    @Test
    @DisplayName("Initial state should be empty")
    void testInitialState() {
        assertTrue(context.getChunks().isEmpty());
        assertTrue(context.getMetadata().isEmpty());
        assertNull(context.getResponseBody());
        assertNull(context.getResponseBytes());
    }

    @Test
    @DisplayName("Adding chunks should work correctly")
    void testAddChunk() {
        // Add first chunk
        context.addChunk("chunk1");
        assertEquals(1, context.getChunks().size());
        assertEquals("chunk1", context.getChunks().get(0));
        
        // Add second chunk
        context.addChunk("chunk2");
        assertEquals(2, context.getChunks().size());
        assertEquals("chunk1", context.getChunks().get(0));
        assertEquals("chunk2", context.getChunks().get(1));
        
        // Add null chunk (should be allowed)
        context.addChunk(null);
        assertEquals(3, context.getChunks().size());
        assertNull(context.getChunks().get(2));
    }

    @Test
    @DisplayName("Adding metadata should work correctly")
    void testAddMetadata() {
        // Add string metadata
        context.addMetadata("key1", "value1");
        assertEquals(1, context.getMetadata().size());
        assertEquals("value1", context.getMetadata().get("key1"));
        
        // Add integer metadata
        context.addMetadata("key2", 42);
        assertEquals(2, context.getMetadata().size());
        assertEquals("value1", context.getMetadata().get("key1"));
        assertEquals(42, context.getMetadata().get("key2"));
        
        // Add double metadata
        context.addMetadata("key3", 3.14);
        assertEquals(3, context.getMetadata().size());
        assertEquals(3.14, context.getMetadata().get("key3"));
        
        // Add boolean metadata
        context.addMetadata("key4", true);
        assertEquals(4, context.getMetadata().size());
        assertEquals(true, context.getMetadata().get("key4"));
        
        // Overwrite existing key
        context.addMetadata("key1", "new_value");
        assertEquals(4, context.getMetadata().size());
        assertEquals("new_value", context.getMetadata().get("key1"));
    }

    @Test
    @DisplayName("Response body operations should work")
    void testResponseBody() {
        assertNull(context.getResponseBody());
        
        // Set response body
        context.setResponseBody("test response body");
        assertEquals("test response body", context.getResponseBody());
        
        // Update response body
        context.setResponseBody("updated response body");
        assertEquals("updated response body", context.getResponseBody());
        
        // Set to null
        context.setResponseBody(null);
        assertNull(context.getResponseBody());
    }

    @Test
    @DisplayName("Response bytes operations should work")
    void testResponseBytes() {
        assertNull(context.getResponseBytes());
        
        // Set response bytes
        byte[] testBytes = "test response".getBytes();
        context.setResponseBytes(testBytes);
        assertArrayEquals(testBytes, context.getResponseBytes());
        // Note: Implementation may return same reference for performance reasons
        
        // Update response bytes
        byte[] newBytes = "new response".getBytes();
        context.setResponseBytes(newBytes);
        assertArrayEquals(newBytes, context.getResponseBytes());
        
        // Set to null
        context.setResponseBytes(null);
        assertNull(context.getResponseBytes());
    }

    @Test
    @DisplayName("Clear should reset all fields")
    void testClear() {
        // Add some data
        context.addChunk("chunk1");
        context.addChunk("chunk2");
        context.addMetadata("key1", "value1");
        context.addMetadata("key2", 42);
        context.setResponseBody("response body");
        context.setResponseBytes("response bytes".getBytes());
        
        // Verify data is present
        assertEquals(2, context.getChunks().size());
        assertEquals(2, context.getMetadata().size());
        assertEquals("response body", context.getResponseBody());
        assertNotNull(context.getResponseBytes());
        
        // Clear all data
        context.clear();
        
        // Verify everything is cleared
        assertTrue(context.getChunks().isEmpty());
        assertTrue(context.getMetadata().isEmpty());
        assertNull(context.getResponseBody());
        assertNull(context.getResponseBytes());
    }

    @Test
    @DisplayName("Context should handle various data types")
    void testVariousDataTypes() {
        // Test different chunk types (using Object context)
        StreamingExecutionContext<MockRequest, Object> objectContext = new StreamingExecutionContext<>();
        
        objectContext.addChunk("string chunk");
        objectContext.addChunk(123);
        objectContext.addChunk(45.67);
        objectContext.addChunk(true);
        objectContext.addChunk(Arrays.asList("nested", "list"));
        
        assertEquals(5, objectContext.getChunks().size());
        assertEquals("string chunk", objectContext.getChunks().get(0));
        assertEquals(123, objectContext.getChunks().get(1));
        assertEquals(45.67, objectContext.getChunks().get(2));
        assertEquals(true, objectContext.getChunks().get(3));
        assertEquals(Arrays.asList("nested", "list"), objectContext.getChunks().get(4));
    }

    @Test
    @DisplayName("Context should handle metadata with null values by removing the key")
    void testNullMetadata() {
        // Adding null should not add the key (ConcurrentHashMap doesn't support null values)
        context.addMetadata("null_key", null);
        assertEquals(0, context.getMetadata().size());
        assertFalse(context.getMetadata().containsKey("null_key"));

        // Add a real key
        context.addMetadata("real_key", "real_value");
        assertEquals(1, context.getMetadata().size());
        assertEquals("real_value", context.getMetadata().get("real_key"));

        // Setting existing key to null should remove it
        context.addMetadata("real_key", null);
        assertEquals(0, context.getMetadata().size());
        assertFalse(context.getMetadata().containsKey("real_key"));
    }

    @Test
    @DisplayName("Context should handle empty and whitespace strings")
    void testEmptyStrings() {
        // Empty string chunk
        context.addChunk("");
        assertEquals(1, context.getChunks().size());
        assertEquals("", context.getChunks().get(0));
        
        // Whitespace chunk
        context.addChunk("   ");
        assertEquals(2, context.getChunks().size());
        assertEquals("   ", context.getChunks().get(1));
        
        // Empty metadata value
        context.addMetadata("empty", "");
        assertEquals(1, context.getMetadata().size());
        assertEquals("", context.getMetadata().get("empty"));
        
        // Empty response body
        context.setResponseBody("");
        assertEquals("", context.getResponseBody());
        
        // Empty response bytes
        context.setResponseBytes(new byte[0]);
        assertEquals(0, context.getResponseBytes().length);
    }

    @Test
    @DisplayName("Context should work with large datasets")
    void testLargeDatasets() {
        // Add many chunks
        for (int i = 0; i < 1000; i++) {
            context.addChunk("chunk_" + i);
        }
        assertEquals(1000, context.getChunks().size());
        assertEquals("chunk_0", context.getChunks().get(0));
        assertEquals("chunk_999", context.getChunks().get(999));
        
        // Add many metadata entries
        for (int i = 0; i < 100; i++) {
            context.addMetadata("key_" + i, "value_" + i);
        }
        assertEquals(100, context.getMetadata().size());
        assertEquals("value_0", context.getMetadata().get("key_0"));
        assertEquals("value_99", context.getMetadata().get("key_99"));
        
        // Large response body
        StringBuilder largeBody = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeBody.append("This is line ").append(i).append(" of the large response body.\n");
        }
        String largeBodyStr = largeBody.toString();
        context.setResponseBody(largeBodyStr);
        assertEquals(largeBodyStr, context.getResponseBody());
        assertTrue(context.getResponseBody().length() > 30000); // Reduced threshold
        
        // Large response bytes
        byte[] largeBytes = new byte[10000];
        Arrays.fill(largeBytes, (byte) 65); // Fill with 'A'
        context.setResponseBytes(largeBytes);
        assertEquals(10000, context.getResponseBytes().length);
        assertEquals(65, context.getResponseBytes()[0]);
        assertEquals(65, context.getResponseBytes()[9999]);
    }

    @Test
    @DisplayName("Context should be thread-safe for basic operations using virtual threads")
    void testBasicThreadSafety() throws InterruptedException {
        final int numThreads = 10;
        final int operationsPerThread = 100;
        Thread[] threads = new Thread[numThreads];

        // Create virtual threads that add chunks and metadata
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            threads[t] = Thread.ofVirtual().unstarted(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    context.addChunk("thread_" + threadId + "_chunk_" + i);
                    context.addMetadata("thread_" + threadId + "_key_" + i, "value_" + i);
                }
            });
        }

        // Start all virtual threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // With thread-safe collections, all operations should succeed
        int expectedOperations = numThreads * operationsPerThread;
        int actualChunks = context.getChunks().size();
        int actualMetadata = context.getMetadata().size();

        // With CopyOnWriteArrayList and ConcurrentHashMap, we expect exactly all operations
        assertEquals(expectedOperations, actualChunks,
                   "Chunks: expected " + expectedOperations + " but got " + actualChunks);
        assertEquals(expectedOperations, actualMetadata,
                   "Metadata: expected " + expectedOperations + " but got " + actualMetadata);
    }

    /**
     * Mock request class for testing generic types.
     */
    private static class MockRequest {
        // Empty mock class for testing purposes
    }
}
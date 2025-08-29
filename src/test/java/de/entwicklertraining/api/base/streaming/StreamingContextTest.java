package de.entwicklertraining.api.base.streaming;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Unit tests for StreamingContext class.
 * Tests the new StreamingContext class introduced in version 2.1.0.
 */
public class StreamingContextTest {

    @Test
    @DisplayName("Builder should create context correctly")
    void testBuilder() {
        List<String> chunks = Arrays.asList("chunk1", "chunk2", "chunk3");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tokens", 100);
        metadata.put("model", "test-model");
        
        long startTime = System.currentTimeMillis();
        long endTime = startTime + 5000;
        
        StreamingContext<String> context = StreamingContext.<String>builder()
            .completed(true)
            .canceled(false)
            .linesProcessed(10)
            .chunks(chunks)
            .metadata(metadata)
            .startTime(startTime)
            .endTime(endTime)
            .build();
        
        assertTrue(context.isSuccess());
        assertTrue(context.isCompleted());
        assertFalse(context.isCanceled());
        assertFalse(context.getError().isPresent());
        assertEquals(10, context.getLinesProcessed());
        assertEquals(3, context.getChunks().size());
        assertEquals("chunk1", context.getChunks().get(0));
        assertEquals("chunk2", context.getChunks().get(1));
        assertEquals("chunk3", context.getChunks().get(2));
        assertEquals(2, context.getMetadata().size());
        assertEquals(100, context.getMetadata().get("tokens"));
        assertEquals("test-model", context.getMetadata().get("model"));
        assertEquals(startTime, context.getStartTimeMillis());
        assertEquals(endTime, context.getEndTimeMillis());
        assertEquals(5000, context.getDurationMillis());
    }

    @Test
    @DisplayName("Builder should handle individual chunk additions")
    void testBuilderWithIndividualChunks() {
        StreamingContext<String> context = StreamingContext.<String>builder()
            .completed(true)
            .addChunk("first")
            .addChunk("second")
            .addMetadata("key1", "value1")
            .addMetadata("key2", 42)
            .linesProcessed(5)
            .build();
        
        assertTrue(context.isSuccess());
        assertEquals(2, context.getChunks().size());
        assertEquals("first", context.getChunks().get(0));
        assertEquals("second", context.getChunks().get(1));
        assertEquals(2, context.getMetadata().size());
        assertEquals("value1", context.getMetadata().get("key1"));
        assertEquals(42, context.getMetadata().get("key2"));
        assertEquals(5, context.getLinesProcessed());
    }

    @Test
    @DisplayName("Error context should not be success")
    void testErrorContext() {
        RuntimeException error = new RuntimeException("Test error");
        
        StreamingContext<String> context = StreamingContext.<String>builder()
            .completed(false)
            .canceled(false)
            .error(error)
            .linesProcessed(3)
            .addChunk("partial")
            .build();
        
        assertFalse(context.isSuccess());
        assertFalse(context.isCompleted());
        assertFalse(context.isCanceled());
        assertTrue(context.getError().isPresent());
        assertEquals(error, context.getError().get());
        assertEquals(3, context.getLinesProcessed());
        assertEquals(1, context.getChunks().size());
        assertEquals("partial", context.getChunks().get(0));
    }

    @Test
    @DisplayName("Canceled context should not be success")
    void testCanceledContext() {
        StreamingContext<String> context = StreamingContext.<String>builder()
            .completed(false)
            .canceled(true)
            .linesProcessed(7)
            .addChunk("before_cancel")
            .build();
        
        assertFalse(context.isSuccess());
        assertFalse(context.isCompleted());
        assertTrue(context.isCanceled());
        assertFalse(context.getError().isPresent());
        assertEquals(7, context.getLinesProcessed());
        assertEquals(1, context.getChunks().size());
        assertEquals("before_cancel", context.getChunks().get(0));
    }

    @Test
    @DisplayName("Success should require completion without error or cancellation")
    void testSuccessConditions() {
        // Success case
        StreamingContext<String> success = StreamingContext.<String>builder()
            .completed(true)
            .canceled(false)
            .build();
        assertTrue(success.isSuccess());
        
        // Not completed
        StreamingContext<String> notCompleted = StreamingContext.<String>builder()
            .completed(false)
            .canceled(false)
            .build();
        assertFalse(notCompleted.isSuccess());
        
        // Completed but canceled
        StreamingContext<String> completedCanceled = StreamingContext.<String>builder()
            .completed(true)
            .canceled(true)
            .build();
        assertFalse(completedCanceled.isSuccess());
        
        // Completed but with error
        StreamingContext<String> completedWithError = StreamingContext.<String>builder()
            .completed(true)
            .canceled(false)
            .error(new RuntimeException("error"))
            .build();
        assertFalse(completedWithError.isSuccess());
    }

    @Test
    @DisplayName("Metadata access should work correctly")
    void testMetadataAccess() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("string_key", "string_value");
        metadata.put("int_key", 123);
        metadata.put("double_key", 45.67);
        metadata.put("bool_key", true);
        
        StreamingContext<String> context = StreamingContext.<String>builder()
            .completed(true)
            .metadata(metadata)
            .build();
        
        // Test basic metadata access
        assertEquals(4, context.getMetadata().size());
        assertEquals("string_value", context.getMetadata().get("string_key"));
        assertEquals(123, context.getMetadata().get("int_key"));
        assertEquals(45.67, context.getMetadata().get("double_key"));
        assertEquals(true, context.getMetadata().get("bool_key"));
        
        // Test getMetadataValue
        assertTrue(context.getMetadataValue("string_key").isPresent());
        assertEquals("string_value", context.getMetadataValue("string_key").get());
        assertFalse(context.getMetadataValue("nonexistent").isPresent());
        
        // Test typed getMetadataValue
        Optional<String> stringVal = context.getMetadataValue("string_key", String.class);
        assertTrue(stringVal.isPresent());
        assertEquals("string_value", stringVal.get());
        
        Optional<Integer> intVal = context.getMetadataValue("int_key", Integer.class);
        assertTrue(intVal.isPresent());
        assertEquals(123, intVal.get().intValue());
        
        Optional<Double> doubleVal = context.getMetadataValue("double_key", Double.class);
        assertTrue(doubleVal.isPresent());
        assertEquals(45.67, doubleVal.get(), 0.001);
        
        Optional<Boolean> boolVal = context.getMetadataValue("bool_key", Boolean.class);
        assertTrue(boolVal.isPresent());
        assertEquals(true, boolVal.get());
        
        // Test wrong type
        Optional<Integer> wrongType = context.getMetadataValue("string_key", Integer.class);
        assertFalse(wrongType.isPresent());
        
        // Test nonexistent key with type
        Optional<String> nonexistent = context.getMetadataValue("nonexistent", String.class);
        assertFalse(nonexistent.isPresent());
    }

    @Test
    @DisplayName("Lists and maps should be immutable")
    void testImmutability() {
        List<String> originalChunks = new ArrayList<>(Arrays.asList("chunk1", "chunk2"));
        Map<String, Object> originalMetadata = new HashMap<>();
        originalMetadata.put("key", "value");
        
        StreamingContext<String> context = StreamingContext.<String>builder()
            .completed(true)
            .chunks(originalChunks)
            .metadata(originalMetadata)
            .build();
        
        // Get collections from context
        List<String> contextChunks = context.getChunks();
        Map<String, Object> contextMetadata = context.getMetadata();
        
        // Verify they are different instances
        assertNotSame(originalChunks, contextChunks);
        assertNotSame(originalMetadata, contextMetadata);
        
        // Verify original modifications don't affect context
        originalChunks.add("chunk3");
        originalMetadata.put("key2", "value2");
        
        assertEquals(2, contextChunks.size());
        assertEquals(1, contextMetadata.size());
        
        // Verify context collections are immutable
        assertThrows(UnsupportedOperationException.class, () -> 
            contextChunks.add("should_fail"));
        assertThrows(UnsupportedOperationException.class, () -> 
            contextMetadata.put("should", "fail"));
    }

    @Test
    @DisplayName("Time methods should work correctly")
    void testTimeMethods() {
        long startTime = 1000000L;
        long endTime = 1005000L; // 5 seconds later
        
        StreamingContext<String> context = StreamingContext.<String>builder()
            .completed(true)
            .startTime(startTime)
            .endTime(endTime)
            .build();
        
        assertEquals(startTime, context.getStartTimeMillis());
        assertEquals(endTime, context.getEndTimeMillis());
        assertEquals(5000L, context.getDurationMillis());
    }

    @Test
    @DisplayName("toString should provide useful information")
    void testToString() {
        StreamingContext<String> success = StreamingContext.<String>builder()
            .completed(true)
            .linesProcessed(5)
            .addChunk("chunk1")
            .addChunk("chunk2")
            .addMetadata("key", "value")
            .build();
        
        String str = success.toString();
        assertTrue(str.contains("success=true"));
        assertTrue(str.contains("completed=true"));
        assertTrue(str.contains("canceled=false"));
        assertTrue(str.contains("linesProcessed=5"));
        assertTrue(str.contains("chunks=2"));
        assertTrue(str.contains("metadata=1"));
        
        // Test with error
        RuntimeException error = new RuntimeException("test error");
        StreamingContext<String> errorContext = StreamingContext.<String>builder()
            .completed(false)
            .error(error)
            .build();
        
        String errorStr = errorContext.toString();
        assertTrue(errorStr.contains("success=false"));
        assertTrue(errorStr.contains("error=RuntimeException"));
    }

    @Test
    @DisplayName("Default values should be sensible")
    void testDefaultValues() {
        StreamingContext<String> context = StreamingContext.<String>builder()
            .build();
        
        assertFalse(context.isSuccess());
        assertFalse(context.isCompleted());
        assertFalse(context.isCanceled());
        assertFalse(context.getError().isPresent());
        assertEquals(0, context.getLinesProcessed());
        assertTrue(context.getChunks().isEmpty());
        assertTrue(context.getMetadata().isEmpty());
        assertTrue(context.getStartTimeMillis() > 0); // Should have default timestamp
        assertTrue(context.getEndTimeMillis() > 0); // Should have default timestamp
        assertTrue(context.getDurationMillis() >= 0);
    }
}
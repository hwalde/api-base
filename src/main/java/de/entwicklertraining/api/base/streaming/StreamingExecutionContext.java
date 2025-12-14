package de.entwicklertraining.api.base.streaming;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Internal context for collecting data during streaming execution.
 * This class accumulates chunks and metadata as they arrive during streaming.
 * 
 * @param <T> The type of request being executed
 * @param <U> The type of data chunks
 * @since 2.1.0
 */
public class StreamingExecutionContext<T, U> {
    
    /**
     * Default constructor for StreamingExecutionContext.
     */
    public StreamingExecutionContext() {}
    
    private final List<U> chunks = new CopyOnWriteArrayList<>();
    private final Map<String, Object> metadata = new ConcurrentHashMap<>();
    private String responseBody;
    private byte[] responseBytes;
    
    /**
     * Adds a chunk to the context.
     * 
     * @param chunk The chunk to add
     */
    public void addChunk(U chunk) {
        chunks.add(chunk);
    }
    
    /**
     * Gets all collected chunks.
     * 
     * @return The list of chunks
     */
    public List<U> getChunks() {
        return chunks;
    }
    
    /**
     * Adds metadata to the context.
     * If value is null, the key will be removed from metadata.
     *
     * @param key The metadata key
     * @param value The metadata value (null removes the key)
     */
    public void addMetadata(String key, Object value) {
        if (value == null) {
            metadata.remove(key);
        } else {
            metadata.put(key, value);
        }
    }
    
    /**
     * Gets all collected metadata.
     * 
     * @return The metadata map
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    /**
     * Sets the response body (for non-streaming responses).
     * 
     * @param responseBody The response body
     */
    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }
    
    /**
     * Gets the response body.
     * 
     * @return The response body
     */
    public String getResponseBody() {
        return responseBody;
    }
    
    /**
     * Sets the response bytes (for binary responses).
     * 
     * @param responseBytes The response bytes
     */
    public void setResponseBytes(byte[] responseBytes) {
        this.responseBytes = responseBytes;
    }
    
    /**
     * Gets the response bytes.
     * 
     * @return The response bytes
     */
    public byte[] getResponseBytes() {
        return responseBytes;
    }
    
    /**
     * Clears all collected data.
     */
    public void clear() {
        chunks.clear();
        metadata.clear();
        responseBody = null;
        responseBytes = null;
    }
}
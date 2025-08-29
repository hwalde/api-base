package de.entwicklertraining.api.base.streaming;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Contains the complete context and results of a streaming operation.
 * This class replaces StreamingApiResponse and provides all information
 * about a completed or in-progress streaming operation.
 * 
 * @param <T> The type of data chunks received during streaming
 * @since 2.1.0
 */
public class StreamingContext<T> {
    
    private final boolean completed;
    private final boolean canceled;
    private final Throwable error;
    private final int linesProcessed;
    private final List<T> chunks;
    private final Map<String, Object> metadata;
    private final long startTimeMillis;
    private final long endTimeMillis;
    
    /**
     * Private constructor - use builder pattern.
     */
    private StreamingContext(Builder<T> builder) {
        this.completed = builder.completed;
        this.canceled = builder.canceled;
        this.error = builder.error;
        this.linesProcessed = builder.linesProcessed;
        this.chunks = Collections.unmodifiableList(new ArrayList<>(builder.chunks));
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
        this.startTimeMillis = builder.startTimeMillis;
        this.endTimeMillis = builder.endTimeMillis;
    }
    
    /**
     * Checks if the streaming operation completed successfully.
     * 
     * @return true if completed without cancellation or error
     */
    public boolean isSuccess() {
        return completed && !canceled && error == null;
    }
    
    /**
     * Checks if the streaming operation is complete.
     * 
     * @return true if the stream reached its natural end
     */
    public boolean isCompleted() {
        return completed;
    }
    
    /**
     * Checks if the streaming operation was canceled.
     * 
     * @return true if the stream was canceled before completion
     */
    public boolean isCanceled() {
        return canceled;
    }
    
    /**
     * Gets any error that occurred during streaming.
     * 
     * @return Optional containing the error, or empty if no error
     */
    public Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }
    
    /**
     * Gets the number of lines processed during streaming.
     * 
     * @return The count of processed lines
     */
    public int getLinesProcessed() {
        return linesProcessed;
    }
    
    /**
     * Gets all chunks received during streaming.
     * 
     * @return Unmodifiable list of chunks
     */
    public List<T> getChunks() {
        return chunks;
    }
    
    /**
     * Gets metadata collected during streaming.
     * 
     * @return Unmodifiable map of metadata
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    /**
     * Gets a specific metadata value.
     * 
     * @param key The metadata key
     * @return Optional containing the value if present
     */
    public Optional<Object> getMetadataValue(String key) {
        return Optional.ofNullable(metadata.get(key));
    }
    
    /**
     * Gets a typed metadata value.
     * 
     * @param key The metadata key
     * @param type The expected type class
     * @param <V> The value type
     * @return Optional containing the typed value if present and correct type
     */
    @SuppressWarnings("unchecked")
    public <V> Optional<V> getMetadataValue(String key, Class<V> type) {
        Object value = metadata.get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of((V) value);
        }
        return Optional.empty();
    }
    
    /**
     * Gets the start time of the streaming operation.
     * 
     * @return Start time in milliseconds since epoch
     */
    public long getStartTimeMillis() {
        return startTimeMillis;
    }
    
    /**
     * Gets the end time of the streaming operation.
     * 
     * @return End time in milliseconds since epoch
     */
    public long getEndTimeMillis() {
        return endTimeMillis;
    }
    
    /**
     * Gets the duration of the streaming operation.
     * 
     * @return Duration in milliseconds
     */
    public long getDurationMillis() {
        return endTimeMillis - startTimeMillis;
    }
    
    /**
     * Creates a new builder for StreamingContext.
     * 
     * @param <T> The type of chunks
     * @return A new builder instance
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }
    
    /**
     * Creates a StreamingContext from the current StreamingResult.
     * This is used internally to convert from the old streaming result format.
     * 
     * @param result The streaming result from ApiClient
     * @param chunks The collected chunks
     * @param metadata The collected metadata
     * @param <T> The type of chunks
     * @return A new StreamingContext instance
     */
    public static <T> StreamingContext<T> fromStreamingResult(
            de.entwicklertraining.api.base.ApiClient.StreamingResult<T> result,
            List<T> chunks,
            Map<String, Object> metadata,
            long startTimeMillis,
            long endTimeMillis) {
        
        Builder<T> builder = new Builder<T>()
            .completed(result.isCompleted())
            .canceled(result.isCanceled())
            .linesProcessed(result.getLinesProcessed())
            .startTime(startTimeMillis)
            .endTime(endTimeMillis);
        
        if (result.getError() != null) {
            builder.error(result.getError());
        }
        
        if (chunks != null) {
            builder.chunks(chunks);
        }
        
        if (metadata != null) {
            builder.metadata(metadata);
        }
        
        return builder.build();
    }
    
    /**
     * Builder for StreamingContext.
     * 
     * @param <T> The type of chunks
     */
    public static class Builder<T> {
        private boolean completed = false;
        private boolean canceled = false;
        private Throwable error = null;
        private int linesProcessed = 0;
        private List<T> chunks = new ArrayList<>();
        private Map<String, Object> metadata = new HashMap<>();
        private long startTimeMillis = System.currentTimeMillis();
        private long endTimeMillis = System.currentTimeMillis();
        
        private Builder() {}
        
        public Builder<T> completed(boolean completed) {
            this.completed = completed;
            return this;
        }
        
        public Builder<T> canceled(boolean canceled) {
            this.canceled = canceled;
            return this;
        }
        
        public Builder<T> error(Throwable error) {
            this.error = error;
            return this;
        }
        
        public Builder<T> linesProcessed(int linesProcessed) {
            this.linesProcessed = linesProcessed;
            return this;
        }
        
        public Builder<T> addChunk(T chunk) {
            this.chunks.add(chunk);
            return this;
        }
        
        public Builder<T> chunks(List<T> chunks) {
            this.chunks = new ArrayList<>(chunks);
            return this;
        }
        
        public Builder<T> addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }
        
        public Builder<T> metadata(Map<String, Object> metadata) {
            this.metadata = new HashMap<>(metadata);
            return this;
        }
        
        public Builder<T> startTime(long startTimeMillis) {
            this.startTimeMillis = startTimeMillis;
            return this;
        }
        
        public Builder<T> endTime(long endTimeMillis) {
            this.endTimeMillis = endTimeMillis;
            return this;
        }
        
        /**
         * Builds the StreamingContext instance.
         * 
         * @return A new StreamingContext instance
         */
        public StreamingContext<T> build() {
            return new StreamingContext<>(this);
        }
    }
    
    @Override
    public String toString() {
        return "StreamingContext{" +
            "success=" + isSuccess() +
            ", completed=" + completed +
            ", canceled=" + canceled +
            ", error=" + (error != null ? error.getClass().getSimpleName() : "none") +
            ", linesProcessed=" + linesProcessed +
            ", chunks=" + chunks.size() +
            ", metadata=" + metadata.size() + " entries" +
            ", duration=" + getDurationMillis() + "ms" +
            '}';
    }
}
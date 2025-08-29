package de.entwicklertraining.api.base.streaming;

import java.time.Duration;
import java.util.Optional;

/**
 * Configuration class for streaming-specific settings.
 * 
 * <p>This class provides fine-grained control over streaming behavior,
 * including buffer management, timeout settings, and reconnection policies.
 */
public class StreamingConfig {
    
    private final int bufferSize;
    private final Duration streamTimeout;
    private final boolean enableReconnect;
    private final int maxReconnectAttempts;
    private final Duration reconnectDelay;
    private final boolean collectData;
    private final int maxCollectedDataSize;
    private final Optional<String> lastEventId;
    
    private StreamingConfig(Builder builder) {
        this.bufferSize = builder.bufferSize;
        this.streamTimeout = builder.streamTimeout;
        this.enableReconnect = builder.enableReconnect;
        this.maxReconnectAttempts = builder.maxReconnectAttempts;
        this.reconnectDelay = builder.reconnectDelay;
        this.collectData = builder.collectData;
        this.maxCollectedDataSize = builder.maxCollectedDataSize;
        this.lastEventId = Optional.ofNullable(builder.lastEventId);
    }
    
    /**
     * Returns the buffer size for reading streaming data.
     * 
     * @return Buffer size in bytes
     */
    public int getBufferSize() {
        return bufferSize;
    }
    
    /**
     * Returns the maximum time to wait for streaming data.
     * 
     * @return Stream timeout duration
     */
    public Duration getStreamTimeout() {
        return streamTimeout;
    }
    
    /**
     * Returns whether automatic reconnection is enabled.
     * 
     * @return true if reconnection is enabled
     */
    public boolean isReconnectEnabled() {
        return enableReconnect;
    }
    
    /**
     * Returns the maximum number of reconnection attempts.
     * 
     * @return Maximum reconnect attempts
     */
    public int getMaxReconnectAttempts() {
        return maxReconnectAttempts;
    }
    
    /**
     * Returns the delay between reconnection attempts.
     * 
     * @return Reconnect delay duration
     */
    public Duration getReconnectDelay() {
        return reconnectDelay;
    }
    
    /**
     * Returns whether streamed data should be collected and stored.
     * 
     * <p>When enabled, all streamed data chunks are kept in memory
     * for later access. Disable for memory-sensitive applications.
     * 
     * @return true if data collection is enabled
     */
    public boolean isDataCollectionEnabled() {
        return collectData;
    }
    
    /**
     * Returns the maximum size of collected data in bytes.
     * 
     * <p>When data collection is enabled, this limits memory usage
     * by discarding old data when the limit is reached.
     * 
     * @return Maximum collected data size in bytes
     */
    public int getMaxCollectedDataSize() {
        return maxCollectedDataSize;
    }
    
    /**
     * Returns the last event ID for resuming streams.
     * 
     * <p>Used with Server-Sent Events to resume streaming from a specific point
     * after disconnection. The server should support this feature.
     * 
     * @return Optional last event ID
     */
    public Optional<String> getLastEventId() {
        return lastEventId;
    }
    
    /**
     * Creates a new builder for StreamingConfig.
     * 
     * @return A new builder instance with default values
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder class for creating StreamingConfig instances.
     */
    public static class Builder {
        
        /**
         * Default constructor for StreamingConfig Builder.
         */
        public Builder() {}
        private int bufferSize = 8192;
        private Duration streamTimeout = Duration.ofMinutes(5);
        private boolean enableReconnect = true;
        private int maxReconnectAttempts = 3;
        private Duration reconnectDelay = Duration.ofSeconds(1);
        private boolean collectData = false;
        private int maxCollectedDataSize = 10 * 1024 * 1024; // 10MB
        private String lastEventId = null;
        
        /**
         * Sets the buffer size for streaming operations.
         * 
         * @param bufferSize the buffer size in bytes, must be positive
         * @return this builder instance for method chaining
         */
        public Builder bufferSize(int bufferSize) {
            if (bufferSize <= 0) {
                throw new IllegalArgumentException("Buffer size must be positive");
            }
            this.bufferSize = bufferSize;
            return this;
        }
        
        /**
         * Sets the timeout for streaming operations.
         * 
         * @param timeout the timeout duration, must be positive
         * @return this builder instance for method chaining
         */
        public Builder streamTimeout(Duration timeout) {
            if (timeout == null || timeout.isNegative()) {
                throw new IllegalArgumentException("Stream timeout must be positive");
            }
            this.streamTimeout = timeout;
            return this;
        }
        
        /**
         * Enables or disables automatic reconnection for streaming operations.
         * 
         * @param enable true to enable reconnection, false to disable
         * @return this builder instance for method chaining
         */
        public Builder enableReconnect(boolean enable) {
            this.enableReconnect = enable;
            return this;
        }
        
        /**
         * Sets the maximum number of reconnection attempts.
         * 
         * @param maxAttempts the maximum number of reconnect attempts, must be non-negative
         * @return this builder instance for method chaining
         */
        public Builder maxReconnectAttempts(int maxAttempts) {
            if (maxAttempts < 0) {
                throw new IllegalArgumentException("Max reconnect attempts cannot be negative");
            }
            this.maxReconnectAttempts = maxAttempts;
            return this;
        }
        
        /**
         * Sets the delay between reconnection attempts.
         * 
         * @param delay the delay duration between reconnect attempts, must be positive
         * @return this builder instance for method chaining
         */
        public Builder reconnectDelay(Duration delay) {
            if (delay == null || delay.isNegative()) {
                throw new IllegalArgumentException("Reconnect delay must be positive");
            }
            this.reconnectDelay = delay;
            return this;
        }
        
        /**
         * Enables or disables data collection during streaming.
         * 
         * @param collect true to collect data, false to disable data collection
         * @return this builder instance for method chaining
         */
        public Builder collectData(boolean collect) {
            this.collectData = collect;
            return this;
        }
        
        /**
         * Sets the maximum size for collected data.
         * 
         * @param maxSize the maximum size in bytes for collected data, must be positive
         * @return this builder instance for method chaining
         */
        public Builder maxCollectedDataSize(int maxSize) {
            if (maxSize <= 0) {
                throw new IllegalArgumentException("Max collected data size must be positive");
            }
            this.maxCollectedDataSize = maxSize;
            return this;
        }
        
        /**
         * Sets the last event ID for resuming streaming from a specific point.
         * 
         * @param eventId the last event ID to resume from, or null to start from the beginning
         * @return this builder instance for method chaining
         */
        public Builder lastEventId(String eventId) {
            this.lastEventId = eventId;
            return this;
        }
        
        /**
         * Builds a new StreamingConfig instance with the configured settings.
         * 
         * @return a new StreamingConfig instance
         */
        public StreamingConfig build() {
            return new StreamingConfig(this);
        }
    }
}
package de.entwicklertraining.api.base.streaming;

import java.util.Objects;
import java.util.Optional;

/**
 * Contains all information needed for streaming operations.
 * This class encapsulates the streaming configuration and handler,
 * allowing requests to be streaming-capable without requiring separate types.
 * 
 * @since 2.1.0
 */
public class StreamingInfo {
    
    private final boolean enabled;
    private final StreamingFormat format;
    private final StreamingResponseHandler<?> handler;
    private final StreamingConfig config;
    
    /**
     * Creates a non-streaming info object.
     * This is the default for requests that don't use streaming.
     */
    public StreamingInfo() {
        this(false, null, null, null);
    }
    
    /**
     * Creates a streaming info with default configuration.
     * 
     * @param format The streaming format (e.g., SSE, JSON Lines)
     * @param handler The handler to process streaming responses
     */
    public StreamingInfo(StreamingFormat format, StreamingResponseHandler<?> handler) {
        this(true, format, handler, null);
    }
    
    /**
     * Creates a streaming info with custom configuration.
     * 
     * @param format The streaming format
     * @param handler The handler to process streaming responses
     * @param config Custom streaming configuration
     */
    public StreamingInfo(StreamingFormat format, StreamingResponseHandler<?> handler, StreamingConfig config) {
        this(true, format, handler, config);
    }
    
    /**
     * Full constructor for all scenarios.
     * 
     * @param enabled Whether streaming is enabled
     * @param format The streaming format (can be null if not enabled)
     * @param handler The handler (can be null if not enabled)
     * @param config Optional custom configuration
     */
    private StreamingInfo(boolean enabled, StreamingFormat format, StreamingResponseHandler<?> handler, StreamingConfig config) {
        this.enabled = enabled;
        this.format = format;
        this.handler = handler;
        this.config = config;
        
        // Validate that if streaming is enabled, format and handler must be provided
        if (enabled) {
            Objects.requireNonNull(format, "StreamingFormat is required when streaming is enabled");
            Objects.requireNonNull(handler, "StreamingResponseHandler is required when streaming is enabled");
        }
    }
    
    /**
     * Checks if streaming is enabled.
     * 
     * @return true if this request should be processed as a stream
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Gets the streaming format.
     * 
     * @return The streaming format, or null if streaming is not enabled
     */
    public StreamingFormat getFormat() {
        return format;
    }
    
    /**
     * Gets the streaming response handler.
     * 
     * @return The handler, or null if streaming is not enabled
     */
    public StreamingResponseHandler<?> getHandler() {
        return handler;
    }
    
    /**
     * Gets the optional streaming configuration.
     * 
     * @return Optional containing the config if present
     */
    public Optional<StreamingConfig> getConfig() {
        return Optional.ofNullable(config);
    }
    
    /**
     * Gets the streaming configuration or a default if not present.
     * 
     * @return The streaming config, creating a default if necessary
     */
    public StreamingConfig getConfigOrDefault() {
        if (config != null) {
            return config;
        }
        // Create default config based on format
        return StreamingConfig.builder()
            .bufferSize(8192)
            .build();
    }
    
    /**
     * Creates a builder for StreamingInfo.
     * 
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for StreamingInfo.
     */
    public static class Builder {
        private boolean enabled = false;
        private StreamingFormat format;
        private StreamingResponseHandler<?> handler;
        private StreamingConfig config;
        
        private Builder() {}
        
        /**
         * Enables streaming with the specified format.
         * 
         * @param format The streaming format to use
         * @return This builder
         */
        public Builder format(StreamingFormat format) {
            this.enabled = true;
            this.format = format;
            return this;
        }
        
        /**
         * Sets the streaming response handler.
         * 
         * @param handler The handler to process streaming responses
         * @return This builder
         */
        public Builder handler(StreamingResponseHandler<?> handler) {
            this.handler = handler;
            return this;
        }
        
        /**
         * Sets custom streaming configuration.
         * 
         * @param config The streaming configuration
         * @return This builder
         */
        public Builder config(StreamingConfig config) {
            this.config = config;
            return this;
        }
        
        /**
         * Disables streaming (creates a non-streaming info).
         * 
         * @return This builder
         */
        public Builder disabled() {
            this.enabled = false;
            this.format = null;
            this.handler = null;
            this.config = null;
            return this;
        }
        
        /**
         * Builds the StreamingInfo instance.
         * 
         * @return A new StreamingInfo instance
         * @throws IllegalStateException if streaming is enabled but format or handler is missing
         */
        public StreamingInfo build() {
            if (enabled && (format == null || handler == null)) {
                throw new IllegalStateException(
                    "StreamingFormat and StreamingResponseHandler are required when streaming is enabled");
            }
            return new StreamingInfo(enabled, format, handler, config);
        }
    }
    
    @Override
    public String toString() {
        if (!enabled) {
            return "StreamingInfo{enabled=false}";
        }
        return "StreamingInfo{" +
            "enabled=true" +
            ", format=" + format +
            ", handler=" + handler.getClass().getSimpleName() +
            ", config=" + (config != null ? "custom" : "default") +
            '}';
    }
}
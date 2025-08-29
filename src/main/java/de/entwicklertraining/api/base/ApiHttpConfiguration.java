package de.entwicklertraining.api.base;

import de.entwicklertraining.api.base.streaming.StreamingFormat;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Configuration for HTTP transport layer concerns including headers and request modification.
 * This class provides a fluent builder API for configuration and allows for easy customization
 * of HTTP requests across all API calls, including streaming operations.
 * <p>
 * Example usage:
 * <pre>
 * ApiHttpConfiguration httpConfig = ApiHttpConfiguration.builder()
 *     .header("Authorization", "Bearer your-token")
 *     .header("X-Custom-Header", "value")
 *     .requestModifier(builder -> builder.header("X-Request-ID", UUID.randomUUID().toString()))
 *     .enableStreamingSupport()
 *     .streamingFormat(StreamingFormat.SERVER_SENT_EVENTS)
 *     .build();
 * </pre>
 */
public class ApiHttpConfiguration {
    /** Global headers to be added to all requests */
    private final Map<String, String> globalHeaders;

    /** Request modifiers to be applied to all HTTP requests */
    private final List<Consumer<HttpRequest.Builder>> requestModifiers;
    
    /** Whether streaming support is enabled */
    private final boolean streamingEnabled;
    
    /** Default streaming format to use */
    private final StreamingFormat defaultStreamingFormat;
    
    /** Streaming-specific headers */
    private final Map<String, String> streamingHeaders;

    /**
     * Creates a new instance with empty configuration.
     */
    public ApiHttpConfiguration() {
        this.globalHeaders = new HashMap<>();
        this.requestModifiers = new ArrayList<>();
        this.streamingEnabled = false;
        this.defaultStreamingFormat = StreamingFormat.SERVER_SENT_EVENTS;
        this.streamingHeaders = new HashMap<>();
    }

    /**
     * Private constructor used by the Builder.
     *
     * @param builder The builder containing all configuration values
     */
    private ApiHttpConfiguration(Builder builder) {
        this.globalHeaders = Map.copyOf(builder.globalHeaders);
        this.requestModifiers = List.copyOf(builder.requestModifiers);
        this.streamingEnabled = builder.streamingEnabled;
        this.defaultStreamingFormat = builder.defaultStreamingFormat;
        this.streamingHeaders = Map.copyOf(builder.streamingHeaders);
    }

    /**
     * Creates a new Builder instance for constructing ApiHttpConfiguration.
     *
     * @return A new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new Builder pre-populated with the current configuration.
     * Useful for creating a modified copy of an existing configuration.
     *
     * @return A new Builder instance with current settings
     */
    public Builder toBuilder() {
        return new Builder()
                .headers(this.globalHeaders)
                .requestModifiers(this.requestModifiers)
                .streamingEnabled(this.streamingEnabled)
                .streamingFormat(this.defaultStreamingFormat)
                .streamingHeaders(this.streamingHeaders);
    }

    /**
     * Gets the global headers that will be added to all requests.
     *
     * @return An unmodifiable map of global headers
     */
    public Map<String, String> getGlobalHeaders() {
        return globalHeaders;
    }

    /**
     * Gets the request modifiers that will be applied to all HTTP requests.
     *
     * @return An unmodifiable list of request modifiers
     */
    public List<Consumer<HttpRequest.Builder>> getRequestModifiers() {
        return requestModifiers;
    }
    
    /**
     * Returns whether streaming support is enabled.
     *
     * @return true if streaming support is enabled
     */
    public boolean isStreamingEnabled() {
        return streamingEnabled;
    }
    
    /**
     * Gets the default streaming format.
     *
     * @return The default streaming format
     */
    public StreamingFormat getDefaultStreamingFormat() {
        return defaultStreamingFormat;
    }
    
    /**
     * Gets the streaming-specific headers that will be added to streaming requests.
     *
     * @return An unmodifiable map of streaming headers
     */
    public Map<String, String> getStreamingHeaders() {
        return streamingHeaders;
    }
    
    /**
     * Gets all headers that should be applied to a streaming request.
     * This combines global headers with streaming-specific headers,
     * with streaming headers taking precedence.
     *
     * @return A combined map of headers for streaming requests
     */
    public Map<String, String> getStreamingRequestHeaders() {
        Map<String, String> combined = new HashMap<>(globalHeaders);
        combined.putAll(streamingHeaders);
        return combined;
    }

    /**
     * A builder for creating {@link ApiHttpConfiguration} instances with a fluent API.
     * This builder allows for easy configuration of HTTP transport settings, including
     * streaming-specific configurations.
     *
     * <p>Example usage:
     * <pre>
     * ApiHttpConfiguration config = ApiHttpConfiguration.builder()
     *     .header("Authorization", "Bearer token")
     *     .header("X-Custom-Header", "value")
     *     .requestModifier(builder -> builder.header("X-Request-ID", UUID.randomUUID().toString()))
     *     .enableStreamingSupport()
     *     .streamingFormat(StreamingFormat.SERVER_SENT_EVENTS)
     *     .streamingHeader("Cache-Control", "no-cache")
     *     .build();
     * </pre>
     */
    public static class Builder {
        private final Map<String, String> globalHeaders = new HashMap<>();
        private final List<Consumer<HttpRequest.Builder>> requestModifiers = new ArrayList<>();
        private boolean streamingEnabled = false;
        private StreamingFormat defaultStreamingFormat = StreamingFormat.SERVER_SENT_EVENTS;
        private final Map<String, String> streamingHeaders = new HashMap<>();

        /**
         * Creates a new Builder instance.
         */
        public Builder() {}

        /**
         * Adds a global header that will be included in all requests.
         *
         * @param name The header name
         * @param value The header value
         * @return This builder for method chaining
         */
        public Builder header(String name, String value) {
            this.globalHeaders.put(name, value);
            return this;
        }

        /**
         * Adds multiple global headers that will be included in all requests.
         *
         * @param headers Map of header names to values
         * @return This builder for method chaining
         */
        public Builder headers(Map<String, String> headers) {
            this.globalHeaders.putAll(headers);
            return this;
        }

        /**
         * Adds a request modifier that will be applied to all HTTP requests.
         * Request modifiers allow for dynamic modification of the HTTP request builder.
         *
         * @param modifier Consumer that receives and can modify the HttpRequest.Builder
         * @return This builder for method chaining
         */
        public Builder requestModifier(Consumer<HttpRequest.Builder> modifier) {
            this.requestModifiers.add(modifier);
            return this;
        }

        /**
         * Adds multiple request modifiers that will be applied to all HTTP requests.
         *
         * @param modifiers List of consumers that receive and can modify the HttpRequest.Builder
         * @return This builder for method chaining
         */
        public Builder requestModifiers(List<Consumer<HttpRequest.Builder>> modifiers) {
            this.requestModifiers.addAll(modifiers);
            return this;
        }
        
        /**
         * Enables streaming support in the HTTP configuration.
         *
         * @return This builder for method chaining
         */
        public Builder enableStreamingSupport() {
            return streamingEnabled(true);
        }
        
        /**
         * Enables or disables streaming support.
         *
         * @param enabled true to enable streaming support
         * @return This builder for method chaining
         */
        public Builder streamingEnabled(boolean enabled) {
            this.streamingEnabled = enabled;
            return this;
        }
        
        /**
         * Sets the default streaming format.
         *
         * @param format The default streaming format to use
         * @return This builder for method chaining
         */
        public Builder streamingFormat(StreamingFormat format) {
            this.defaultStreamingFormat = format;
            return this;
        }
        
        /**
         * Adds a streaming-specific header that will be included in streaming requests.
         * These headers are applied in addition to global headers, with streaming headers
         * taking precedence if there are conflicts.
         *
         * @param name The header name
         * @param value The header value
         * @return This builder for method chaining
         */
        public Builder streamingHeader(String name, String value) {
            this.streamingHeaders.put(name, value);
            return this;
        }
        
        /**
         * Adds multiple streaming-specific headers.
         *
         * @param headers Map of header names to values for streaming requests
         * @return This builder for method chaining
         */
        public Builder streamingHeaders(Map<String, String> headers) {
            this.streamingHeaders.putAll(headers);
            return this;
        }
        
        /**
         * Configures common streaming headers for Server-Sent Events.
         * This is a convenience method that sets appropriate headers for SSE.
         *
         * @return This builder for method chaining
         */
        public Builder configureForServerSentEvents() {
            return streamingFormat(StreamingFormat.SERVER_SENT_EVENTS)
                    .streamingHeader("Accept", "text/event-stream")
                    .streamingHeader("Cache-Control", "no-cache")
                    .streamingHeader("Connection", "keep-alive");
        }
        
        /**
         * Configures common streaming headers for JSON Lines format.
         * This is a convenience method that sets appropriate headers for NDJSON.
         *
         * @return This builder for method chaining
         */
        public Builder configureForJsonLines() {
            return streamingFormat(StreamingFormat.JSON_LINES)
                    .streamingHeader("Accept", "application/x-ndjson")
                    .streamingHeader("Connection", "keep-alive");
        }
        
        /**
         * Configures common streaming headers for raw text streaming.
         *
         * @return This builder for method chaining
         */
        public Builder configureForRawText() {
            return streamingFormat(StreamingFormat.RAW_TEXT)
                    .streamingHeader("Accept", "text/plain")
                    .streamingHeader("Connection", "keep-alive");
        }

        /**
         * Builds a new {@link ApiHttpConfiguration} instance with the configured settings.
         *
         * @return A new {@link ApiHttpConfiguration} instance
         */
        public ApiHttpConfiguration build() {
            // If streaming is enabled, ensure appropriate default headers are set
            if (streamingEnabled && streamingHeaders.isEmpty()) {
                // Set default streaming headers based on format
                switch (defaultStreamingFormat) {
                    case SERVER_SENT_EVENTS:
                        streamingHeader("Accept", "text/event-stream")
                               .streamingHeader("Cache-Control", "no-cache");
                        break;
                    case JSON_LINES:
                        streamingHeader("Accept", "application/x-ndjson");
                        break;
                    case RAW_TEXT:
                        streamingHeader("Accept", "text/plain");
                        break;
                }
                // Always set keep-alive for streaming
                streamingHeader("Connection", "keep-alive");
            }
            
            return new ApiHttpConfiguration(this);
        }
    }
}
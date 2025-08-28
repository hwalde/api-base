package de.entwicklertraining.api.base;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Configuration for HTTP transport layer concerns including headers and request modification.
 * This class provides a fluent builder API for configuration and allows for easy customization
 * of HTTP requests across all API calls.
 * <p>
 * Example usage:
 * <pre>
 * ApiHttpConfiguration httpConfig = ApiHttpConfiguration.builder()
 *     .header("Authorization", "Bearer your-token")
 *     .header("X-Custom-Header", "value")
 *     .requestModifier(builder -> builder.header("X-Request-ID", UUID.randomUUID().toString()))
 *     .build();
 * </pre>
 */
public class ApiHttpConfiguration {
    /** Global headers to be added to all requests */
    private final Map<String, String> globalHeaders;

    /** Request modifiers to be applied to all HTTP requests */
    private final List<Consumer<HttpRequest.Builder>> requestModifiers;

    /**
     * Creates a new instance with empty configuration.
     */
    public ApiHttpConfiguration() {
        this.globalHeaders = new HashMap<>();
        this.requestModifiers = new ArrayList<>();
    }

    /**
     * Private constructor used by the Builder.
     *
     * @param builder The builder containing all configuration values
     */
    private ApiHttpConfiguration(Builder builder) {
        this.globalHeaders = Map.copyOf(builder.globalHeaders);
        this.requestModifiers = List.copyOf(builder.requestModifiers);
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
                .requestModifiers(this.requestModifiers);
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
     * A builder for creating {@link ApiHttpConfiguration} instances with a fluent API.
     * This builder allows for easy configuration of HTTP transport settings.
     *
     * <p>Example usage:
     * <pre>
     * ApiHttpConfiguration config = ApiHttpConfiguration.builder()
     *     .header("Authorization", "Bearer token")
     *     .header("X-Custom-Header", "value")
     *     .requestModifier(builder -> builder.header("X-Request-ID", UUID.randomUUID().toString()))
     *     .build();
     * </pre>
     */
    public static class Builder {
        private final Map<String, String> globalHeaders = new HashMap<>();
        private final List<Consumer<HttpRequest.Builder>> requestModifiers = new ArrayList<>();

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
         * Builds a new {@link ApiHttpConfiguration} instance with the configured settings.
         *
         * @return A new {@link ApiHttpConfiguration} instance
         */
        public ApiHttpConfiguration build() {
            return new ApiHttpConfiguration(this);
        }
    }
}
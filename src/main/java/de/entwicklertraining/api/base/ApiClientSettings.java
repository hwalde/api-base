package de.entwicklertraining.api.base;

import java.util.Optional;

/**
 * Configuration settings for API client behavior including retry logic, backoff strategy, and authentication.
 * This class provides a fluent builder API for configuration and sensible defaults for all settings.
 * <p>
 * Example usage:
 * <pre>
 * ApiClientSettings settings = ApiClientSettings.builder()
 *     .maxRetries(5)
 *     .initialDelayMs(1000)
 *     .exponentialBase(2.0)
 *     .useJitter(true)
 *     .setBearerAuthenticationKey("your-api-key")
 *     .build();
 * </pre>
 */
public final class ApiClientSettings {
    /** Bearer token for API authentication, if required */
    private String bearerAuthenticationKey;

    /** Maximum number of retry attempts for failed requests */
    private int maxRetries = 10;
    
    /** Initial delay before first retry in milliseconds */
    private long initialDelayMs = 1000; // 1 second
    
    /** Base multiplier for exponential backoff calculation */
    private double exponentialBase = 2.0;
    
    /** Whether to add random jitter to backoff delays to prevent thundering herd */
    private boolean useJitter = true;

    /** 
     * Minimum time in milliseconds remaining before timeout to attempt a final retry.
     * This allows for one last attempt if there's enough time left.
     */
    private int minSleepDurationForFinalRetryInSeconds = 500;
    
    /** 
     * Maximum execution time in seconds to allow for the final retry attempt.
     * This ensures the final retry has enough time to complete.
     */
    private int maxExecutionTimeForFinalRetryInSeconds = 60;

    /**
     * Creates a new instance with default settings.
     * Consider using {@link #builder()} for a more flexible configuration approach.
     */
    public ApiClientSettings() {
        // Default constructor with default values
    }

    /**
     * Private constructor used by the Builder.
     *
     * @param builder The builder containing all configuration values
     */
    private ApiClientSettings(Builder builder) {
        this.bearerAuthenticationKey = builder.bearerAuthenticationKey;
        this.maxRetries = builder.maxRetries;
        this.initialDelayMs = builder.initialDelayMs;
        this.exponentialBase = builder.exponentialBase;
        this.useJitter = builder.useJitter;
        this.minSleepDurationForFinalRetryInSeconds = builder.minSleepDurationForFinalRetryInSeconds;
        this.maxExecutionTimeForFinalRetryInSeconds = builder.maxExecutionTimeForFinalRetryInSeconds;
    }

    /**
     * Creates a new Builder pre-populated with the current settings.
     * Useful for creating a modified copy of an existing configuration.
     *
     * @return A new Builder instance with current settings
     */
    public Builder toBuilder() {
        return new Builder()
                .maxRetries(this.maxRetries)
                .initialDelayMs(this.initialDelayMs)
                .exponentialBase(this.exponentialBase)
                .useJitter(this.useJitter)
                .minSleepDurationForFinalRetryInSeconds(this.minSleepDurationForFinalRetryInSeconds)
                .maxExecutionTimeForFinalRetryInSeconds(this.maxExecutionTimeForFinalRetryInSeconds);
    }

    // --- GETTERS & FLUENT SETTERS ---

    /**
     * Gets the optional bearer authentication token.
     *
     * @return An Optional containing the bearer token if set, empty otherwise
     */
    public Optional<String> getBearerAuthenticationKey() {
        return Optional.ofNullable(bearerAuthenticationKey);
    }

    /**
     * Gets the maximum number of retry attempts for failed requests.
     *
     * @return The maximum number of retries
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Sets the maximum number of retry attempts.
     *
     * @param maxRetries Maximum number of retries (must be >= 0)
     * @return This instance for method chaining
     */
    public ApiClientSettings setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    /**
     * Gets the initial delay before the first retry in milliseconds.
     *
     * @return The initial delay in milliseconds
     */
    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    /**
     * Sets the initial delay before the first retry.
     *
     * @param initialDelayMs Delay in milliseconds (must be >= 0)
     * @return This instance for method chaining
     */
    public ApiClientSettings setInitialDelayMs(long initialDelayMs) {
        this.initialDelayMs = initialDelayMs;
        return this;
    }

    /**
     * Gets the base value used for exponential backoff calculation.
     *
     * @return The exponential base value
     */
    public double getExponentialBase() {
        return exponentialBase;
    }

    /**
     * Sets the base value for exponential backoff calculation.
     * Each retry delay is calculated as: initialDelayMs * (exponentialBase ^ retryNumber)
     *
     * @param exponentialBase The base value (must be > 1.0)
     * @return This instance for method chaining
     * @throws IllegalArgumentException if exponentialBase is lower or equal to 1.0
     */
    public ApiClientSettings setExponentialBase(double exponentialBase) {
        this.exponentialBase = exponentialBase;
        return this;
    }

    /**
     * Checks if jitter is enabled for backoff delays.
     *
     * @return true if jitter is enabled, false otherwise
     */
    public boolean isUseJitter() {
        return useJitter;
    }

    /**
     * Enables or disables jitter for backoff delays.
     * When enabled, adds randomness to retry delays to prevent thundering herd problems.
     *
     * @param useJitter true to enable jitter, false to disable
     * @return This instance for method chaining
     */
    public ApiClientSettings setUseJitter(boolean useJitter) {
        this.useJitter = useJitter;
        return this;
    }

    /**
     * Gets the minimum duration in seconds required to attempt a final retry.
     *
     * @return The minimum duration in seconds
     */
    public int getMinSleepDurationForFinalRetryInSeconds() {
        return minSleepDurationForFinalRetryInSeconds;
    }

    /**
     * Sets the minimum duration required to attempt a final retry.
     * If the remaining time before timeout is less than this value, no final retry will be attempted.
     *
     * @param seconds Minimum duration in seconds (must be >= 0)
     * @return This instance for method chaining
     */
    public ApiClientSettings setMinSleepDurationForFinalRetryInSeconds(int seconds) {
        this.minSleepDurationForFinalRetryInSeconds = seconds;
        return this;
    }

    /**
     * Gets the maximum execution time in seconds allowed for the final retry attempt.
     *
     * @return The maximum execution time in seconds
     */
    public int getMaxExecutionTimeForFinalRetryInSeconds() {
        return maxExecutionTimeForFinalRetryInSeconds;
    }

    /**
     * Sets the maximum execution time allowed for the final retry attempt.
     * This ensures the final retry has sufficient time to complete.
     *
     * @param seconds Maximum execution time in seconds (must be > 0)
     * @return This instance for method chaining
     * @throws IllegalArgumentException if seconds lower or equal 0
     */
    public ApiClientSettings setMaxExecutionTimeForFinalRetryInSeconds(int seconds) {
        this.maxExecutionTimeForFinalRetryInSeconds = seconds;
        return this;
    }

    // --- BUILDER ---

    /**
     * Creates a new Builder instance for constructing ApiClientSettings.
     *
     * @return A new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder for creating {@link ApiClientSettings} instances with a fluent API.
     * This builder allows for easy configuration of client settings with sensible defaults.
     * 
     * <p>Example usage:
     * <pre>
     * ApiClientSettings settings = ApiClientSettings.builder()
     *     .maxRetries(5)
     *     .initialDelayMs(1000)
     *     .exponentialBase(2.0)
     *     .useJitter(true)
     *     .setBearerAuthenticationKey("your-api-key")
     *     .build();
     * </pre>
     */
    public static final class Builder {
        private String bearerAuthenticationKey;
        private int maxRetries = 10;
        private long initialDelayMs = 1000;
        private double exponentialBase = 2.0;
        private boolean useJitter = true;
        private int minSleepDurationForFinalRetryInSeconds = 500;
        private int maxExecutionTimeForFinalRetryInSeconds = 60;

        /**
         * Creates a new Builder instance with default settings.
         */
        public Builder() {}

        /**
         * Sets the Bearer authentication key for API requests.
         *
         * @param key The Bearer token to use for authentication
         * @return This builder for method chaining
         */
        public Builder setBearerAuthenticationKey(String key) {
            this.bearerAuthenticationKey = key;
            return this;
        }

        /**
         * Sets the maximum number of retry attempts for failed requests.
         *
         * @param maxRetries The maximum number of retry attempts
         * @return This builder for method chaining
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the initial delay in milliseconds before the first retry attempt.
         *
         * @param initialDelayMs The initial delay in milliseconds
         * @return This builder for method chaining
         */
        public Builder initialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
            return this;
        }

        /**
         * Sets the base value used for exponential backoff between retry attempts.
         *
         * @param exponentialBase The base value for exponential backoff
         * @return This builder for method chaining
         */
        public Builder exponentialBase(double exponentialBase) {
            this.exponentialBase = exponentialBase;
            return this;
        }

        /**
         * Enables or disables jitter in the retry delay calculation.
         *
         * @param useJitter Whether to use jitter in delay calculations
         * @return This builder for method chaining
         */
        public Builder useJitter(boolean useJitter) {
            this.useJitter = useJitter;
            return this;
        }

        /**
         * Sets the minimum sleep duration in seconds for the final retry attempt.
         *
         * @param seconds The minimum sleep duration in seconds
         * @return This builder for method chaining
         */
        public Builder minSleepDurationForFinalRetryInSeconds(int seconds) {
            this.minSleepDurationForFinalRetryInSeconds = seconds;
            return this;
        }

        /**
         * Sets the maximum execution time in seconds for the final retry attempt.
         *
         * @param seconds The maximum execution time in seconds
         * @return This builder for method chaining
         */
        public Builder maxExecutionTimeForFinalRetryInSeconds(int seconds) {
            this.maxExecutionTimeForFinalRetryInSeconds = seconds;
            return this;
        }

        /**
         * Builds a new {@link ApiClientSettings} instance with the configured settings.
         *
         * @return A new {@link ApiClientSettings} instance
         * @throws IllegalStateException if the configuration is invalid
         */
        public ApiClientSettings build() {
            return new ApiClientSettings(this);
        }
    }
}


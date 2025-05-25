package de.entwicklertraining.api.base;

import java.util.Optional;

/**
 * Holds the settings for retry and backoff behavior used by ApiClient.
 * Provides default values.
 */
public final class ApiClientSettings {
    private String bearerAuthenticationKey;

    private int maxRetries = 10;
    private long initialDelayMs = 1000; // 1 Sekunde
    private double exponentialBase = 2.0;
    private boolean useJitter = true;

    // Wenn die Zeit für einen weiteren Sleep nicht mehr reicht, kann es noch einen finalen Versuch geben.
    private int minSleepDurationForFinalRetryInSeconds = 500;
    private int maxExecutionTimeForFinalRetryInSeconds = 60;

    /**
     * Constructor with default values. Provided for backward compatibility.
     */
    public ApiClientSettings() {
        // Default constructor with default values
    }

    /**
     * Private constructor used by Builder.
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
     * Returns a new Builder initialized with the current field values of this ApiClientSettings instance.
     * This can be useful if you want to copy or tweak an existing settings object.
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

    // --- GETTERS & EXISTING FLUENT SETTERS (OPTIONAL) ---

    public Optional<String> getBearerAuthenticationKey() {
        return Optional.ofNullable(bearerAuthenticationKey);
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * If you want to continue supporting fluent setters on the instance level, keep these.
     * Otherwise, consider removing them to enforce the builder usage.
     */
    public ApiClientSettings setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public ApiClientSettings setInitialDelayMs(long initialDelayMs) {
        this.initialDelayMs = initialDelayMs;
        return this;
    }

    public double getExponentialBase() {
        return exponentialBase;
    }

    public ApiClientSettings setExponentialBase(double exponentialBase) {
        this.exponentialBase = exponentialBase;
        return this;
    }

    public boolean isUseJitter() {
        return useJitter;
    }

    public ApiClientSettings setUseJitter(boolean useJitter) {
        this.useJitter = useJitter;
        return this;
    }

    public int getMinSleepDurationForFinalRetryInSeconds() {
        return minSleepDurationForFinalRetryInSeconds;
    }

    public ApiClientSettings setMinSleepDurationForFinalRetryInSeconds(int seconds) {
        this.minSleepDurationForFinalRetryInSeconds = seconds;
        return this;
    }

    public int getMaxExecutionTimeForFinalRetryInSeconds() {
        return maxExecutionTimeForFinalRetryInSeconds;
    }

    public ApiClientSettings setMaxExecutionTimeForFinalRetryInSeconds(int seconds) {
        this.maxExecutionTimeForFinalRetryInSeconds = seconds;
        return this;
    }

    // --- BUILDER ---

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int maxRetries = 10;
        private long initialDelayMs = 1000;
        private double exponentialBase = 2.0;
        private boolean useJitter = true;
        private int minSleepDurationForFinalRetryInSeconds = 500;
        private int maxExecutionTimeForFinalRetryInSeconds = 60;
        private String bearerAuthenticationKey;

        public Builder() {
            // default values are set above
        }

        public Builder setBearerAuthenticationKey(String key) {
            this.bearerAuthenticationKey = key;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder initialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
            return this;
        }

        public Builder exponentialBase(double exponentialBase) {
            this.exponentialBase = exponentialBase;
            return this;
        }

        public Builder useJitter(boolean useJitter) {
            this.useJitter = useJitter;
            return this;
        }

        public Builder minSleepDurationForFinalRetryInSeconds(int seconds) {
            this.minSleepDurationForFinalRetryInSeconds = seconds;
            return this;
        }

        public Builder maxExecutionTimeForFinalRetryInSeconds(int seconds) {
            this.maxExecutionTimeForFinalRetryInSeconds = seconds;
            return this;
        }

        public ApiClientSettings build() {
            return new ApiClientSettings(this);
        }
    }
}


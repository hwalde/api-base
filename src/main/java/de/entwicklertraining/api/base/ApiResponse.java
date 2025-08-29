package de.entwicklertraining.api.base;

import de.entwicklertraining.api.base.streaming.StreamingContext;
import java.util.Optional;

/**
 * Abstract base class representing a response from an API request.
 * <p>
 * This class serves as the foundation for all API responses in the client library.
 * It provides access to the original request that generated this response,
 * allowing for easy chaining and context preservation.
 * 
 * <p>As of version 2.1.0, responses can optionally contain streaming context
 * for requests that were processed as streams.
 *
 * <p>Subclasses should extend this class to provide type-safe access to
 * response-specific data and functionality.
 *
 * @param <Q> The type of the API request that generated this response
 *
 * @see ApiRequest
 * @since 2.0.0
 */
public abstract class ApiResponse<Q extends ApiRequest<?>> {

    /** The original request that generated this response */
    private final Q request;
    
    /** Optional streaming context if this response was from a streaming operation */
    private StreamingContext<?> streamingContext;

    /**
     * Creates a new API response for the given request.
     *
     * @param request The original request that generated this response
     * @throws NullPointerException if request is null
     */
    protected ApiResponse(Q request) {
        this.request = request;
        this.streamingContext = null;
    }
    
    /**
     * Creates a new API response for the given request with streaming context.
     *
     * @param request The original request that generated this response
     * @param streamingContext The streaming context from a streaming operation
     * @throws NullPointerException if request is null
     * @since 2.1.0
     */
    protected ApiResponse(Q request, StreamingContext<?> streamingContext) {
        this.request = request;
        this.streamingContext = streamingContext;
    }

    /**
     * Gets the original request that generated this response.
     * <p>
     * This is useful for accessing request parameters, headers, or other
     * context that might be needed when processing the response.
     *
     * @return The original API request (never null)
     */
    public Q getRequest() {
        return request;
    }
    
    /**
     * Gets the streaming context if this response was from a streaming operation.
     * 
     * @return Optional containing the streaming context, or empty if not a streaming response
     * @since 2.1.0
     */
    public Optional<StreamingContext<?>> getStreamingContext() {
        return Optional.ofNullable(streamingContext);
    }
    
    /**
     * Checks if this response was from a streaming operation.
     * 
     * @return true if this response contains streaming context
     * @since 2.1.0
     */
    public boolean isStreamingResponse() {
        return streamingContext != null;
    }
    
    /**
     * Sets the streaming context for this response.
     * This is typically called internally by the API client.
     * 
     * @param streamingContext The streaming context to set
     * @since 2.1.0
     */
    protected void setStreamingContext(StreamingContext<?> streamingContext) {
        this.streamingContext = streamingContext;
    }
}

package de.entwicklertraining.api.base;

/**
 * Abstract base class representing a response from an API request.
 * <p>
 * This class serves as the foundation for all API responses in the client library.
 * It provides access to the original request that generated this response,
 * allowing for easy chaining and context preservation.
 *
 * <p>Subclasses should extend this class to provide type-safe access to
 * response-specific data and functionality.
 *
 * @param <Q> The type of the API request that generated this response
 *
 * @see ApiRequest
 */
public abstract class ApiResponse<Q extends ApiRequest<?>> {

    /** The original request that generated this response */
    private final Q request;

    /**
     * Creates a new API response for the given request.
     *
     * @param request The original request that generated this response
     * @throws NullPointerException if request is null
     */
    protected ApiResponse(Q request) {
        this.request = request;
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
}

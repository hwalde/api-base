package de.entwicklertraining.api.base;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * A context object that holds the execution state and response data for an API request.
 * <p>
 * This class is used internally by {@link ApiClient} to pass response data between
 * different stages of request processing, particularly for capturing and logging purposes.
 *
 * @param <T> The type of the API request
 * @param <U> The type of the API response
 */
public final class ApiRequestExecutionContext<T extends ApiRequest<U>, U extends ApiResponse<T>> {

    /** The response body as a string, if the response is text-based */
    private String responseBody;
    
    /** The raw response bytes, used for binary responses */
    private byte[] responseBytes;
    
    /** The active HTTP future for the request (currently unused) */
    private CompletableFuture<HttpResponse<?>> activeFuture;

    /**
     * Gets the response body as a string.
     *
     * @return The response body, or null if the response is binary or not yet set
     */
    public String getResponseBody() {
        return responseBody;
    }

    /**
     * Sets the response body string.
     *
     * @param responseBody The response body content as a string
     */
    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    /**
     * Gets the raw response bytes.
     *
     * @return The response bytes, or null if the response is text-based or not yet set
     */
    public byte[] getResponseBytes() {
        return responseBytes;
    }

    /**
     * Sets the response bytes.
     *
     * @param responseBytes The raw response bytes
     */
    public void setResponseBytes(byte[] responseBytes) {
        this.responseBytes = responseBytes;
    }

}

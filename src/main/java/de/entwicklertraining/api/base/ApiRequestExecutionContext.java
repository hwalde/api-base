package de.entwicklertraining.api.base;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * A context object to store response data (body or bytes) for an API request execution.
 * It is passed into runRequest() to capture the final response payload.
 */
public final class ApiRequestExecutionContext<T extends ApiRequest<U>, U extends ApiResponse<T>> {

    private String responseBody;
    private byte[] responseBytes;
    private CompletableFuture<HttpResponse<?>> activeFuture;

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public byte[] getResponseBytes() {
        return responseBytes;
    }

    public void setResponseBytes(byte[] responseBytes) {
        this.responseBytes = responseBytes;
    }

}

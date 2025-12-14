# api-base Roadmap

This document outlines planned improvements and features for future versions.

## Planned for v3.0.0

### Structured HTTP Exception Hierarchy

**Current State (v2.x):**
HTTP status code exceptions (e.g., `HTTP_400_RequestRejectedException`, `HTTP_401_AuthorizationException`) extend `ApiClientException` directly. Error response bodies are included in the exception message but not programmatically accessible.

**Proposed Improvement:**
Introduce a new `ApiHttpException` base class that provides structured access to HTTP error details:

```java
public class ApiHttpException extends ApiClientException {
    private final int statusCode;
    private final String responseBody;
    private final Map<String, List<String>> responseHeaders;

    public int getStatusCode() { return statusCode; }
    public String getResponseBody() { return responseBody; }
    public Map<String, List<String>> getResponseHeaders() { return responseHeaders; }

    /**
     * Attempts to parse the response body as JSON.
     * @return Optional containing JSONObject if parseable, empty otherwise
     */
    public Optional<JSONObject> getResponseBodyAsJson() { ... }
}
```

**Benefits:**
- Programmatic access to raw response body for custom error parsing
- Access to HTTP status code without string parsing
- Optional JSON parsing for structured error responses
- Better integration with API-specific error handling (e.g., OpenAI, Gemini error formats)

**Migration:**
All existing HTTP status exceptions will extend `ApiHttpException` instead of `ApiClientException` directly. This is a breaking change for code that catches specific exception types and relies on the inheritance hierarchy.

**Example Usage:**
```java
try {
    client.execute(request);
} catch (ApiHttpException e) {
    int status = e.getStatusCode();
    String body = e.getResponseBody();

    // Parse API-specific error format
    e.getResponseBodyAsJson().ifPresent(json -> {
        if (json.has("error")) {
            String message = json.getJSONObject("error").getString("message");
            // Handle specific error
        }
    });
}
```

---

## Ideas for Future Versions

- Circuit breaker pattern for resilience
- Request/response interceptors
- Metrics and observability hooks
- Connection pooling configuration
- Async streaming with backpressure support

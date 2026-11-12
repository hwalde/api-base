# Changelog

All notable changes to this project will be documented in this file.

## [2.3.0] - 2026-11-12
### Added
- **NEW**: HTTP error context on thrown exceptions - the transport records status code, response body and response headers of the failed exchange in a new `ApiErrorContext` value and hands it to the exception it throws: a registered exception class that declares a public `(String message, ApiErrorContext apiErrorContext)` constructor receives it (the message-only constructor is still used otherwise, so registrations from older client libraries keep working), and every status-code exception api-base ships plus `ApiClientException`/`StreamingException`/`StreamingConnectionException` now carries `getApiErrorContext()` and implements the new `ApiErrorContextAware` interface. `ApiErrorContext.of(Throwable)` reads it through the interface and walks the cause chain (capped at 20 hops), so a transport exception wrapped by streaming or async layers still resolves to the recorded context; `header(String)` looks a header up case-insensitively (first value per repeated name). Callers can finally read `Retry-After`, `X-RateLimit-*` or a structured error body from a caught exception instead of string-parsing the message. The record's `toString()` deliberately prints only the status code, the header names and the body length - bodies and header values stay out of logs. Source-compatibility note: adding the `(String, ApiErrorContext)` overload makes a previously compiling bare-`null` call such as `new HTTP_404_NotFoundException(msg, null)` ambiguous at compile time (cast the `null`); this is the only known source-compat edge of this release.
- **NEW**: Response headers on `ApiResponse` - `getResponseHeaders()` / `getResponseHeader(String)` (case-insensitive) expose the HTTP response headers of the exchange the response was parsed from, populated by the transport on the regular and the streaming path (first value per repeated name). `setResponseHeaders(Map)` is public so a layer synthesizing one response from a completed HTTP exchange (e.g. a tool-call loop accumulating a final response over several turns) can copy the headers onto the synthesized response.
- **NEW**: Opt-in `Retry-After` backoff - `ApiClientSettings.builder().honorRetryAfter(true)` (default off) lets the retry backoff honor a server-sent `Retry-After` header: the sleep before the next attempt is at least as long as the server asked for (delta-seconds or the HTTP-date form; unparsable/negative values ignored; an HTTP-date in the past means retry immediately; huge delta-seconds saturate instead of overflowing). The server-provided delay only ever extends the calculated exponential delay and is clamped to the new `maxRetryAfterMs` ceiling (default 5 minutes, `ApiClientSettings.builder().maxRetryAfterMs(...)`), so a hostile or misconfigured server cannot park a retrying thread indefinitely; the request's maximum execution time bounds the sleep further when one is set. Both the regular and the streaming retry loop consult it.

### Fixed
- **FIX**: the Maven build now actually runs the JUnit 5 test suite - without a surefire 3.x pin Maven silently used an ancient surefire that ignores JUnit 5 and reported "Tests run: 0" as success, so the tests present in this repository never ran. The new pin (`maven-surefire-plugin` 3.2.5) makes the full suite run (76 tests green).

## [2.2.2] - 2026-04-01
### Added
- Optional custom `StreamProcessor` support in `StreamingInfo` - allows consumers to override the default factory-created processor via `StreamingInfo.builder().customProcessor(processor).build()`. Enables use cases like raw JSON extraction for tool call parsing in streaming responses.

## [2.2.0] - 2025-12-14
### Added
- **NEW**: Streaming now fully integrated into public `execute()`, `executeWithRetry()`, `executeAsync()`, and `executeAsyncWithRetry()` methods
- **NEW**: Error response body is now read and included in exception messages for streaming requests - enables debugging of API errors
- **NEW**: `ROADMAP.md` documenting planned improvements for v3.0.0

### Fixed
- **CRITICAL**: Fixed thread-safety issue in `StreamingExecutionContext` - now uses `CopyOnWriteArrayList` and `ConcurrentHashMap` instead of `ArrayList` and `HashMap`
- **BREAKING**: `addMetadata(key, null)` now removes the key instead of storing null (ConcurrentHashMap does not support null values)
- Fixed `StreamProcessorFactory.createProcessor()` to use `String.class` for proper OPENAI_STYLE extraction
- Fixed streaming response creation to use `"{}"` instead of empty string to prevent JSON parsing errors

### Changed
- Thread-safety tests now use virtual threads (`Thread.ofVirtual()`)

### Removed
- Removed obsolete documentation files: `GENERIC_ISSUE.md`, `POSSIBLE_ISSUES.md`, `POTENTIAL_BUGS_2.md`, `THE_STATE_OF_STREAMING.md`
- Removed `UnsupportedOperationException` placeholders from streaming execute methods

## [2.1.0] - 2025-08-29
### Added
- **NEW**: Unified streaming architecture with `StreamingInfo` and `StreamingContext` classes
- **NEW**: Native streaming support in `ApiRequestBuilderBase` via `stream()` methods
- **NEW**: Smart `execute()` methods in `ApiClient` that automatically handle streaming based on request configuration
- **NEW**: `executeAsync()` and `executeAsyncWithRetry()` methods for asynchronous execution
- **NEW**: `StreamingExecutionContext` for collecting streaming data during execution
- **NEW**: Optional `ApiClient` parameter in `ApiRequestBuilderBase` constructor for direct execution support
- **NEW**: Enhanced cancellation support with `setCancelToken()`, `setCancelFuture()`, and `setCancelSupplier()` methods
- **NEW**: Streaming context in `ApiResponse` for unified response handling

### Changed
- **BREAKING**: Removed `StreamingApiRequest<T>` and `StreamingApiResponse<T>` interfaces
- **BREAKING**: `executeWithExponentialBackoff()` deprecated in favor of `executeWithRetry()`  
- **BREAKING**: `sendRequest()` and `sendRequestWithRetry()` methods deprecated in favor of `execute()` methods
- **BREAKING**: Streaming configuration moved from request types to `StreamingInfo` class
- **BREAKING**: `ApiRequestBuilderBase` now has optional `ApiClient` constructor parameter
- **BREAKING**: `ApiRequest` now includes `StreamingInfo` field from builder
- **BREAKING**: `ApiResponse` now includes optional `StreamingContext` field

### Removed
- **BREAKING**: Removed `StreamingApiRequest<T>` interface - use normal `ApiRequest` with streaming configuration
- **BREAKING**: Removed `StreamingApiResponse<T>` interface - use normal `ApiResponse` with streaming context
- **BREAKING**: Removed duplicate `sendStreamingRequest()` methods - use unified `execute()` methods

### Migration Guide
- Replace `StreamingApiRequest<T>` implementations with normal `ApiRequest` classes
- Use `builder.stream(handler)` instead of separate streaming request types
- Replace `client.sendStreamingRequest(request, handler)` with `request.stream(handler).executeAsync()` 
- Replace `executeWithExponentialBackoff()` calls with `executeWithRetry()`
- Replace `sendRequest()` calls with `execute()`
- Access streaming results via `response.getStreamingContext()` instead of separate streaming response types

## [2.0.0] - 2025-08-29
### Added
- **NEW**: Complete streaming API support with `StreamingApiRequest<T>` and `StreamingApiResponse<T>` classes
- **NEW**: Streaming response handlers with `StreamingResponseHandler<T>` interface
- **NEW**: Streaming configuration options via `StreamingConfig` class
- **NEW**: Support for multiple streaming formats (SSE, JSON Lines, etc.) via `StreamingFormat` enum
- **NEW**: `StreamingResult<T>` for handling streaming operation results
- **NEW**: Example implementation in `StreamingApiExample` demonstrating streaming usage

### Changed
- **BREAKING**: Relaxed generic constraint in `ApiRequest` from `R extends ApiResponse<? extends ApiRequest<?>>` to `R extends ApiResponse<?>`
  - This change was necessary to support the new streaming API inheritance hierarchy
  - Response classes that access request-specific methods now need to cast `getRequest()` result to the specific request type
  - Enables proper inheritance chain: `StreamingApiRequest<T>` extends `ApiRequest<StreamingApiResponse<T>>`

## [1.0.5] - 2025-08-28
### Added
- New `ApiHttpConfiguration` class for HTTP transport layer configuration
- Support for global headers via `ApiHttpConfiguration.header(name, value)`
- Support for request modifiers via `ApiHttpConfiguration.requestModifier(Consumer<HttpRequest.Builder>)`
- `toBuilder()` method in `ApiHttpConfiguration` for configuration inheritance
- New constructor `ApiClient(ApiClientSettings, ApiHttpConfiguration)` for flexible configuration
- Enhanced authentication support (Bearer tokens, API keys, custom headers)

### Changed  
- **BREAKING**: Removed `bearerAuthenticationKey` from `ApiClientSettings`
- **BREAKING**: Removed `setBearerAuthenticationKey()` from `ApiClientSettings.Builder`
- `ApiClientSettings` class is no longer `final`
- `ApiClientSettings.Builder` class is no longer `final`
- Separated concerns: `ApiClientSettings` for technical parameters, `ApiHttpConfiguration` for HTTP transport
- Bearer authentication now configured via `ApiHttpConfiguration.header("Authorization", "Bearer token")`

### Migration Guide
- Replace `ApiClientSettings.builder().setBearerAuthenticationKey("token")` with `ApiHttpConfiguration.builder().header("Authorization", "Bearer token")`
- Update client constructors to accept both `ApiClientSettings` and `ApiHttpConfiguration`
- Existing `ApiClient(ApiClientSettings)` constructor still works with empty HTTP configuration


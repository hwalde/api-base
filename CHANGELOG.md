# Changelog

All notable changes to this project will be documented in this file.

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


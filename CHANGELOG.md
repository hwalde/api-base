# Changelog

All notable changes to this project will be documented in this file.

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


# CLAUDE.md

This file provides guidance to Coding Agents when working with code in this repository.

## Project Overview

This is `api-base`, a Java library providing a foundation for building type-safe, resilient REST API clients. It uses Java 21+ features including virtual threads and modern `java.net.http.HttpClient`. The library provides clear separation between technical client configuration and HTTP transport settings.

## Maven Commands

- **Build**: `mvn compile` or `mvn package`
- **Test**: `mvn test`
- **Clean build**: `mvn clean package`
- **Install locally**: `mvn install`
- **Generate sources**: `mvn source:jar`
- **Generate Javadoc**: `mvn javadoc:jar`

## Core Architecture

The library follows a builder pattern with these key abstractions:

### Main Classes
- **`ApiClient`**: Abstract base class for API clients. Handles HTTP requests, retries with exponential backoff, virtual threads, and status code exception mapping.
- **`ApiRequest<R>`**: Abstract base for requests. Subclasses define endpoint, method, body, and response parsing.
- **`ApiResponse<Q>`**: Base for responses. Contains reference to originating request.
- **`ApiRequestBuilderBase<B, R>`**: Fluent builder for configuring requests (timeouts, capture hooks).
- **`ApiClientSettings`**: Configuration for retry behavior, backoff strategy, and execution timeouts.
- **`ApiHttpConfiguration`**: Configuration for HTTP transport layer including global headers and request modifiers.

### Key Patterns
1. **Client Implementation**: Extend `ApiClient`, call `setBaseUrl()` in constructor, register status code exceptions with `registerStatusCodeException()`. Can be configured with both `ApiClientSettings` and `ApiHttpConfiguration`
2. **Request Implementation**: Extend `ApiRequest<ResponseType>`, implement `getRelativeUrl()`, `getHttpMethod()`, `getBody()`, `createResponse(String)`
3. **Response Implementation**: Extend `ApiResponse<RequestType>`, store parsed data
4. **Builder Implementation**: Extend `ApiRequestBuilderBase`, provide fluent configuration methods

### Exception Handling
The library provides built-in HTTP status code exceptions:
- `HTTP_400_RequestRejectedException`
- `HTTP_401_AuthorizationException` 
- `HTTP_402_PaymentRequiredException`
- `HTTP_403_PermissionDeniedException`
- `HTTP_404_NotFoundException`
- `HTTP_422_UnprocessableEntityException`
- `HTTP_429_RateLimitOrQuotaException`
- `HTTP_500_ServerErrorException`
- `HTTP_503_ServerUnavailableException`
- `HTTP_504_ServerTimeoutException`
- `ApiClientException` (client-side errors)
- `ApiTimeoutException` (timeouts/cancellation)
- `ApiResponseUnusableException` (invalid response content)

### Configuration Architecture
- **`ApiClientSettings`**: Technical client parameters (retries, backoff, timeouts)
- **`ApiHttpConfiguration`**: HTTP transport settings (headers, authentication, request modifiers)
- **Separation of Concerns**: Clear distinction between retry logic and HTTP transport configuration

### Virtual Threads
Uses virtual threads for HTTP operations and cancel watching. The HTTP client is bound to a virtual thread executor.

### HTTP Configuration
The `ApiHttpConfiguration` allows for:
- Global headers applied to all requests via `header()` method
- Bearer token authentication via `header("Authorization", "Bearer token")`
- Custom authentication methods via global headers
- Request modifiers for dynamic request manipulation
- Configuration inheritance via `toBuilder()` method

## Dependencies
- Jackson for JSON handling (`com.fasterxml.jackson.core:jackson-databind`)
- SLF4J for logging (`org.slf4j:slf4j-api`)
- JUnit 5 for testing (`org.junit.jupiter`)

## Usage Examples

### Basic Client Setup
```java
// Technical settings
ApiClientSettings settings = ApiClientSettings.builder()
    .maxRetries(3)
    .initialDelayMs(1000)
    .build();

// HTTP configuration
ApiHttpConfiguration httpConfig = ApiHttpConfiguration.builder()
    .header("Authorization", "Bearer your-token")
    .header("X-Custom-Header", "value")
    .requestModifier(builder -> builder.header("X-Request-ID", UUID.randomUUID().toString()))
    .build();

// Create client
MyApiClient client = new MyApiClient(settings, httpConfig);
```

### Alternative Authentication Methods
```java
// API Key authentication
ApiHttpConfiguration apiKeyConfig = ApiHttpConfiguration.builder()
    .header("X-API-Key", "your-api-key")
    .build();

// Basic authentication
ApiHttpConfiguration basicAuthConfig = ApiHttpConfiguration.builder()
    .header("Authorization", "Basic " + Base64.getEncoder().encodeToString("user:pass".getBytes()))
    .build();
```

## Java Version
Requires Java 21+ (configured with `maven.compiler.release=21`)

## Maven Central Publishing

### Voraussetzungen

#### 1. Maven Central Credentials konfigurieren

Die Maven Central Credentials müssen in `~/.m2/settings.xml` konfiguriert sein:

```xml
<servers>
  <server>
    <id>central</id>
    <username>dein-sonatype-token-username</username>
    <password>dein-sonatype-token-password</password>
  </server>
</servers>
```

#### 2. GPG-Schlüssel prüfen und erstellen

**Prüfen ob GPG-Schlüssel vorhanden ist:**
```bash
gpg --list-secret-keys --keyid-format SHORT
```

**Falls kein GPG-Schlüssel vorhanden ist, erstellen:**
```bash
# GPG-Schlüssel generieren (ohne Passwort für automatisierte Builds)
gpg --batch --gen-key <<EOF
Key-Type: RSA
Key-Length: 4096
Name-Real: {Dein Name}
Name-Email: {deine@email.de}
Expire-Date: 0
%no-protection
%commit
EOF

# Schlüssel-ID abrufen
gpg --list-secret-keys --keyid-format SHORT
# Ausgabe zeigt z.B.: sec rsa4096/A3694C5B ...

# Schlüssel zu Keyserver hochladen (WICHTIG für Maven Central!)
gpg --keyserver keyserver.ubuntu.com --send-keys {KEY_ID}
```

### Deployment auf Maven Central

```bash
# Deploy mit GPG-Signierung
mvn clean deploy -DskipTests -Dgpg.keyname={KEY_ID}
```

**Erwartete Ausgabe bei Erfolg:**
```
[INFO] Uploaded bundle successfully, deployment name: Deployment, deploymentId: xxx
[INFO] Deployment xxx has been validated.
[INFO] BUILD SUCCESS
```

### Verifizierung

Nach erfolgreichem Deploy:
- **Maven Central:** Nach 10-30 Minuten verfügbar unter https://central.sonatype.com
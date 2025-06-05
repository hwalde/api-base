# API-Base: Your Foundation for Robust and Modern Java API Clients! 🚀

[![Java CI with Maven](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/entwicklertraining/api-base) [![Maven Central](https://img.shields.io/maven-central/v/de.entwicklertraining/api-base.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:de.entwicklertraining%20a:api-base) [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Tired of boilerplate code when creating API clients? Want to focus on the logic of your API integration instead of dealing with timeouts, retries, and HTTP client configuration?

**Then `api-base` is exactly what you need!** 🎉

`api-base` is a lightweight yet powerful Java library that provides you with a solid foundation for creating type-safe, resilient, and easy-to-use API clients. Built with modern Java features like `java.net.http.HttpClient` and virtual threads, it's performant and future-proof.

## ✨ Why You'll Love `api-base`

* **Simplicity in Focus:** Define your API endpoints clearly and concisely.
* **Built-in Resilience:** Automatic exponential backoff retries for transient errors.
* **Modern Java Usage:** Leverages `java.net.http.HttpClient` and uses virtual threads for non-blocking operations.
* **Flexible & Extensible:** Register custom exceptions for specific HTTP status codes.
* **Configurable:** Detailed configuration options for timeouts, retry behavior, and authentication.
* **Type Safety:** Use generics for your requests and responses.
* **Easy Debugging:** Optional hooks for capturing request and response data.
* **Cancellation Support:** Built-in ability to cancel ongoing requests.
* **Out-of-the-Box Support for:**
    * GET, POST, DELETE requests
    * JSON and binary payloads
    * Bearer token authentication
    * Additional HTTP headers

## ⚙️ Installation

Simply add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>de.entwicklertraining</groupId>
    <artifactId>api-base</artifactId>
    <version>1.0.3</version>
</dependency>
```

## 🚀 Getting Started: A Simple Example

Imagine you want to create a client for the [JSONPlaceholder API](https://jsonplaceholder.typicode.com/) to retrieve todos. It's this easy with `api-base`:

**1. Define your response data model (POJO):**

```java
// Todo.java
public class Todo {
    private int userId;
    private int id;
    private String title;
    private boolean completed;

    // Constructor, getters and setters (or use Lombok)
    public Todo() {}

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    @Override
    public String toString() {
        return "Todo{" +
               "userId=" + userId +
               ", id=" + id +
               ", title='" + title + '\'' +
               ", completed=" + completed +
               '}';
    }
}
```

**2. Create your API request (`ApiRequest`):**

```java
// GetTodoRequest.java
import de.entwicklertraining.api.base.ApiRequest;
import de.entwicklertraining.api.base.ApiRequestBuilderBase;
// (Import your Todo and GetTodoResponse classes here)
// Assumption: You have a JSON library like Jackson or Gson for deserialization.
// For this example, the deserialization is simplified.
import com.fasterxml.jackson.databind.ObjectMapper; // Example with Jackson

public class GetTodoRequest extends ApiRequest<GetTodoResponse> {
    private final int todoId;

    private GetTodoRequest(Builder builder) {
        super(builder);
        this.todoId = builder.todoId;
    }

    @Override
    public String getRelativeUrl() {
        return "/todos/" + todoId;
    }

    @Override
    public String getHttpMethod() {
        return "GET";
    }

    @Override
    public String getBody() {
        return null; // No body for GET
    }

    @Override
    public GetTodoResponse createResponse(String responseBody) {
        // In a real application, you would use a JSON library here
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Todo todo = objectMapper.readValue(responseBody, Todo.class);
            return new GetTodoResponse(this, todo);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing Todo response: " + e.getMessage(), e);
        }
    }

    public static Builder builder(int todoId) {
        return new Builder(todoId);
    }

    public static class Builder extends ApiRequestBuilderBase<Builder, GetTodoRequest> {
        private final int todoId;

        public Builder(int todoId) {
            this.todoId = todoId;
        }

        @Override
        public GetTodoRequest build() {
            return new GetTodoRequest(this);
        }

        @Override
        public GetTodoResponse executeWithExponentialBackoff() {
            return JsonPlaceholderClient.getInstance().sendRequestWithExponentialBackoff(build());
        }

        @Override
        public GetTodoResponse execute() {
            return JsonPlaceholderClient.getInstance().sendRequest(build());
        }
    }
}
```

**3. Create your API response (`ApiResponse`):**

```java
// GetTodoResponse.java
import de.entwicklertraining.api.base.ApiResponse;
// (Import your Todo and GetTodoRequest classes here)

public class GetTodoResponse extends ApiResponse<GetTodoRequest> {
    private final Todo todo;

    public GetTodoResponse(GetTodoRequest request, Todo todo) {
        super(request);
        this.todo = todo;
    }

    public Todo getTodo() {
        return todo;
    }
}
```

**4. Create your API client (`ApiClient`):**

```java
// JsonPlaceholderClient.java
import de.entwicklertraining.api.base.ApiClient;
import de.entwicklertraining.api.base.ApiClientSettings;

public class JsonPlaceholderClient extends ApiClient {
    private static JsonPlaceholderClient instance;

    public JsonPlaceholderClient(ApiClientSettings settings) {
        super(settings);
        setBaseUrl("[https://jsonplaceholder.typicode.com](https://jsonplaceholder.typicode.com)");

        // Register specific exception handlers for status codes
        registerStatusCodeException(404, HTTP_404_NotFoundException.class, "Todo not found", false);
        // You could register more handlers here, e.g., for 401, 500, etc.
        registerStatusCodeException(500, HTTP_500_ServerErrorException.class, "Server error at JSONPlaceholder", true); // retry = true
    }

    public static synchronized JsonPlaceholderClient getInstance() {
        if (instance == null) {
            instance = new JsonPlaceholderClient(ApiClientSettings.builder().build());
        }
        return instance;
    }

    // Factory method for the RequestBuilder
    public GetTodoRequest.Builder getTodo(int todoId) {
        return GetTodoRequest.builder(todoId);
    }
}
```

**5. Put it all together and use it:**

```java
// Main.java
import de.entwicklertraining.api.base.ApiClientSettings;
import de.entwicklertraining.api.base.ApiCallCaptureInput; // For capture example
// (Import your Client, Request, Response, and Todo classes here)

public class Main {
    public static void main(String[] args) {
        // Create and execute request
        try {
            System.out.println("Retrieving Todo with ID 1...");
            GetTodoResponse response = GetTodoRequest.builder(1)
                    // Optional: Request-specific settings
                    .maxExecutionTimeInSeconds(10)
                    .captureOnSuccess(Main::logSuccess) // Optional: Capture on success
                    .captureOnError(Main::logError)     // Optional: Capture on error
                    .executeWithExponentialBackoff(); // With retries

            Todo todo = response.getTodo();
            System.out.println("Received: " + todo);

            System.out.println("\nTrying to retrieve a non-existent Todo (ID 9999)...");
            // Example for error handling
            GetTodoRequest.builder(9999)
                  .captureOnError(Main::logError)
                  .execute(); // Without retries for this example

        } catch (ApiClient.HTTP_404_NotFoundException e) {
            System.err.println("Error retrieving Todo: " + e.getMessage());
        } catch (ApiClient.ApiTimeoutException e) {
            System.err.println("Timeout retrieving Todo: " + e.getMessage());
        } catch (ApiClient.ApiClientException e) {
            System.err.println("General API client error: " + e.getMessage());
        } catch (RuntimeException e) {
            System.err.println("Unexpected runtime error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Example for capture consumers
    private static void logSuccess(ApiCallCaptureInput captureInput) {
        System.out.println("[CAPTURE SUCCESS] Duration: " +
                (captureInput.endTime().toEpochMilli() - captureInput.startTime().toEpochMilli()) + "ms, " +
                "Input: " + captureInput.inputData() + ", Output: " + captureInput.outputData().substring(0, Math.min(100, captureInput.outputData().length())) + "...");
    }

    private static void logError(ApiCallCaptureInput captureInput) {
        System.err.println("[CAPTURE ERROR] Duration: " +
                (captureInput.endTime().toEpochMilli() - captureInput.startTime().toEpochMilli()) + "ms, " +
                "Input: " + captureInput.inputData() + ", Exception: " + captureInput.exceptionClass() + " - " + captureInput.exceptionMessage());
    }
}
```

See? With `api-base`, you can focus on what matters: interacting with the API. The library takes care of the rest for you!

## 💡 Core Concepts

* **`ApiClient`**: Your main interface to the API. Extend this class to create your specific client. Here you configure the base URL and register custom exception handlers for HTTP status codes.
* **`ApiRequest<R extends ApiResponse<?>>`**: Represents a single request. Extend this class to define endpoint, HTTP method, request body, and the logic for creating the `ApiResponse`.
* **`ApiResponse<Q extends ApiRequest<?>>`**: Represents the response to a request. Contains the parsed data and a reference to the original `ApiRequest`.
* **`ApiRequestBuilderBase<B, R>`**: The base for your fluent request builders, to create and configure requests comfortably (e.g., timeouts, capture hooks).
* **`ApiClientSettings`**: Configures the behavior of your `ApiClient`, especially for retries (count, delay, jitter, etc.) and authentication.
* **`ApiCallCaptureInput`**: A record that contains all relevant information about an API call for logging or debugging purposes, if capturing is enabled.

## 🔬 Advanced Usage

* **Custom Error Handling**:
```java
// Inside your ApiClient constructor
registerStatusCodeException(401, MyAuthenticationException.class, "Invalid credentials", false);
registerStatusCodeException(429, MyRateLimitException.class, "Too many requests", true); // true for retry
```

* **Request-specific Timeouts and Capture Hooks**:
```java
MyResponse response = MyRequest.builder()
    .maxExecutionTimeInSeconds(5) // Timeout for this specific request
    .captureOnSuccess(successData -> System.out.println("YAY: " + successData.outputData()))
    .captureOnError(errorData -> System.err.println("OOPS: " + errorData.exceptionMessage()))
    .execute();
```

* **Binary Data Sending/Receiving**: Override `isBinaryResponse()`, `createResponse(byte[])` in your `ApiRequest` and possibly `getBodyBytes()` for binary uploads.

* **Request Cancellation**:
```java
// Create a state that you can change from outside
final AtomicBoolean cancelFlag = new AtomicBoolean(false);

// Start the request in a new thread to be able to cancel it
Thread apiCallThread = new Thread(() -> {
    try {
        MyResponse response = MyRequest.builder()
            .setCancelSupplier(cancelFlag::get) // Pass the supplier
            .executeWithExponentialBackoff();
        System.out.println("Response received: " + response);
    } catch (ApiTimeoutException e) {
        if (cancelFlag.get()) {
            System.out.println("Request was successfully cancelled.");
        } else {
            System.err.println("Request Timeout: " + e.getMessage());
        }
    } catch (Exception e) {
        System.err.println("Error: " + e.getMessage());
    }
});
apiCallThread.start();

// ... later, to cancel the request ...
// Thread.sleep(1000); // Wait a bit
// if (apiCallThread.isAlive()) {
//     System.out.println("Trying to cancel request...");
//     cancelFlag.set(true);
// }
```

## 🤝 Participate & Contribute

This project thrives on community involvement! We welcome contributions of all kinds:

* 🐛 **Report Bugs**: Create an issue if you find a bug.
* 💡 **Feature Suggestions**: Have an idea on how to make `api-base` even better? Share it with us!
* 🧑‍💻 **Code Contributions**: Fork the repository, create a feature branch, and send us a pull request.

Please read our (yet to be created) `CONTRIBUTING.md` for details on the development process.

## 📜 License

This project is published under the MIT License. See the `LICENSE` file for details.

---

We hope `api-base` significantly eases your work with APIs! Happy coding!

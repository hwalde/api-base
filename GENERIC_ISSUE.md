# Generic Type System Problem in Streaming API

## Das Problem

Wir haben ein fundamentales Generic Type System Problem in der Streaming API, das die Kompilierung verhindert. Die Architektur soll eine einheitliche, typsichere API mit Code-Wiederverwendung und vollständiger CancellationToken-Unterstützung bieten.

## Was wir erreichen wollen

### 1. Einheitliche API-Architektur
- `StreamingApiRequest<T>` soll von `ApiRequest<R>` erben
- Dadurch wird Code-Wiederverwendung ermöglicht (alle Methoden wie `getIsCanceledSupplier()`, `hasCaptureOnSuccess()` etc.)
- Einheitliche Parameter-Typen in `ApiClient` Methoden

### 2. CancellationToken Integration
- `StreamingApiRequest` soll die CancellationToken-Funktionalität von `ApiRequest` erben
- CancellationToken-Checks in `executeRequestWithRetry()` und `executeStreamingWithRetry()` sollen funktionieren
- Builder-Pattern für CancellationToken (`setCancelToken()`, `setCancelSupplier()`) soll wiederverwendbar sein

### 3. Typsicherheit
- `settings.getBeforeSendAction().accept(request)` in `ApiClient.sendStreamingRequest()` soll funktionieren
- `BeforeSendAction` erwartet `Consumer<ApiRequest<?>>`
- `request` ist `StreamingApiRequest<T>` - soll kompatibel sein

## Aktuelle Architektur

### ApiRequest Klasse
```java
public abstract class ApiRequest<R extends ApiResponse<? extends ApiRequest<?>>> {
    // Konstruktor
    protected ApiRequest(ApiRequestBuilderBase<?, ?> builderBase) { ... }
    
    // Methoden wie:
    public abstract String getRelativeUrl();
    public Supplier<Boolean> getIsCanceledSupplier();
    public abstract R createResponse(String responseBody);
    // etc.
}
```

### ApiResponse Klasse  
```java
public abstract class ApiResponse<Q extends ApiRequest<?>> {
    // Konstruktor
    public ApiResponse(Q request) { ... }
}
```

### StreamingApiRequest (gewünschter Zustand)
```java
public abstract class StreamingApiRequest<T> extends ApiRequest<StreamingApiResponse<T>> {
    protected StreamingApiRequest(ApiRequestBuilderBase<?, ?> builderBase) {
        super(builderBase);
    }
    
    // Streaming-spezifische Methoden
    public abstract boolean isStreaming();
    public abstract StreamingFormat getStreamingFormat();
    public abstract Optional<StreamingConfig> getStreamingConfig();
}
```

### StreamingApiResponse (gewünschter Zustand)
```java
public abstract class StreamingApiResponse<T> extends ApiResponse<StreamingApiRequest<T>> {
    // Streaming-spezifische Implementierung
}
```

## Konkrete Kompilierungsfehler

### 1. Generic Type Constraint Violation
```
/StreamingApiRequest.java:[21,85] Typargument StreamingApiResponse<T> liegt nicht innerhalb des gültigen Bereichs von Typvariable R
```

**Problem:** `ApiRequest<R>` erwartet `R extends ApiResponse<? extends ApiRequest<?>>`, aber `StreamingApiResponse<T>` erfüllt diese Constraint nicht.

**Details:** 
- `StreamingApiResponse<T>` würde zu `ApiResponse<StreamingApiRequest<T>>` 
- Das bedeutet `R = StreamingApiResponse<T>`
- Aber `StreamingApiResponse<T>` extends `ApiResponse<StreamingApiRequest<T>>`
- Das führt zu einer zirkulären Generic-Abhängigkeit

### 2. Konstruktor-Probleme
```
/StreamingApiResponse.java:[26,17] Konstruktor ApiResponse in Klasse ApiResponse<Q> kann nicht auf die angegebenen Typen angewendet werden.
```

**Problem:** `ApiResponse<Q>` Konstruktor erwartet `Q request` Parameter, aber `StreamingApiResponse` ruft Konstruktor ohne Parameter auf.

**Details:**
- `ApiResponse` Konstruktor: `public ApiResponse(Q request)`
- `StreamingApiResponse` versucht: impliziter Default-Konstruktor Aufruf
- `Q` wäre `StreamingApiRequest<T>` - aber das existiert noch nicht zum Konstruktor-Zeitpunkt

### 3. Type Inference Probleme in ApiClient
```
/ApiClient.java:[1228,51] Inkompatible Typen: T kann nicht in ApiRequest<?> konvertiert werden
```

**Problem:** In `sendStreamingRequest()` Methoden:
```java
public <T extends StreamingApiRequest<U>, U> CompletableFuture<StreamingResult<U>> 
sendStreamingRequest(T request, StreamingResponseHandler<U> handler) {
    if (settings.getBeforeSendAction() != null) {
        settings.getBeforeSendAction().accept(request);  // ❌ Fehler hier
    }
}
```

**Details:**
- `settings.getBeforeSendAction()` ist `Consumer<ApiRequest<?>>`
- `request` ist `T extends StreamingApiRequest<U>`  
- Aber `StreamingApiRequest<U>` ist noch kein Subtyp von `ApiRequest<?>`

### 4. Method Resolution Conflicts
```
/StreamingApiExample.java:[168,23] Inferenzvariable T hat inkompatible Grenzwerte
    Obere Grenzen: StreamingApiRequest<U>
    Untere Grenzwerte: ExampleStreamingRequest
```

**Problem:** `ExampleStreamingRequest` implementiert noch das alte Interface, aber wird als Klasse erwartet.

## Circular Dependency Problem

Das Kernproblem ist eine **zirkuläre Generic-Abhängigkeit**:

1. `StreamingApiRequest<T>` extends `ApiRequest<StreamingApiResponse<T>>`
2. `StreamingApiResponse<T>` extends `ApiResponse<StreamingApiRequest<T>>`
3. `ApiRequest<R>` erwartet `R extends ApiResponse<? extends ApiRequest<?>>`

Das führt zu:
- `StreamingApiRequest<T>` → braucht `StreamingApiResponse<T>` als R
- `StreamingApiResponse<T>` → braucht `StreamingApiRequest<T>` als Q  
- `ApiRequest<R>` constraint → R muss `ApiResponse<? extends ApiRequest<?>>` sein
- Das führt zu: `StreamingApiResponse<T> extends ApiResponse<StreamingApiRequest<T>>` wobei `StreamingApiRequest<T> extends ApiRequest<...>`

## Weitere Probleme

### StreamingApiClientSettings Constructor
```
/StreamingApiClientSettings.java:[59,9] Konstruktor für ApiClientSettings(...) nicht geeignet
```
- Versucht `ApiClientSettings` Konstruktor mit 7 Parametern aufzurufen
- Aber verfügbare Konstruktoren haben andere Signaturen

### Builder Pattern Conflicts  
```
builder() in StreamingApiClientSettings kann nicht builder() in ApiClientSettings ausblenden
```
- `StreamingApiClientSettings.Builder` ist nicht kompatibel mit `ApiClientSettings.Builder`
- Return Type Mismatch bei überschriebenen Methoden

### Example Class @Override Issues
- `ExampleStreamingRequest` hat viele `@Override` Methoden die nicht mehr existieren
- War für Interface-Design geschrieben, funktioniert nicht mit Klassen-Design

## Abhängigkeiten

Das Generic-Problem blockiert:
1. **CancellationToken Integration Testing** - können nicht kompilieren um Tests zu laufen
2. **Code-Wiederverwendung** - StreamingApiRequest kann ApiRequest Funktionalität nicht erben  
3. **Typsicherheit** - ApiClient Methoden können nicht mit beiden Request-Typen arbeiten
4. **Builder-Pattern** - Streaming Requests können Standard-Builder nicht nutzen

## Was funktioniert

- **CancellationToken4J Library** - kompiliert und alle 103 Tests bestehen
- **ApiRequest/ApiResponse Basis-Klassen** - funktionieren für normale Requests
- **ApiClient CancellationToken Integration** - würde funktionieren wenn Streaming API nicht wäre
- **CancellationException Integration** - richtig von RuntimeException abgeleitet

Das Problem ist rein in der **Generic Type Hierarchy** und **Circular Dependencies** zwischen Streaming API Klassen.
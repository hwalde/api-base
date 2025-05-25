package de.entwicklertraining.api.base;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Eine generische, von speziellen API-Implementierungen unabhängige
 * Request-Abstraktion.
 *
 * Subklassen muessen Folgendes spezifizieren:
 *  - getUri()          => z.B. "https://example.com/endpoint"
 *  - getHttpMethod()   => "POST", "GET", "DELETE", ...
 *  - getBody()         => Request-Payload (z.B. JSON-String)
 *  - createResponse(...) => Fabrikmethode, die ein passendes ApiResponse erzeugt
 *
 * Diese Klasse verwaltet außerdem die maximale Ausführungszeit (maxExecutionTimeInSeconds).
 */
public abstract class ApiRequest<R extends ApiResponse<?>> {

    private final int maxExecutionTimeInSeconds;

    // NEU: Zusätzliche Header, die neben Content-Type gesendet werden.
    // Dies ist wichtig z.B. für Authorization: Bearer ...
    private final Map<String, String> additionalHeaders = new HashMap<>();

    // Neue Felder für das Capture
    private final Consumer<ApiCallCaptureInput> captureOnSuccess;
    private final Consumer<ApiCallCaptureInput> captureOnError;

    // Neu: Cancel-Flag
    private volatile boolean canceled = false;

    private final Supplier<Boolean> isCanceledSupplier;

    protected ApiRequest(ApiRequestBuilderBase<?, ?> builderBase) {
        this.maxExecutionTimeInSeconds = builderBase.maxExecutionTimeInSeconds;
        this.captureOnSuccess = builderBase.captureOnSuccess;
        this.captureOnError = builderBase.captureOnError;
        this.isCanceledSupplier = builderBase.isCanceledSupplier;
    }

    public int getMaxExecutionTimeInSeconds() {
        return maxExecutionTimeInSeconds;
    }

    public boolean hasCaptureOnSuccess() {
        return captureOnSuccess != null;
    }

    public boolean hasCaptureOnError() {
        return captureOnError != null;
    }

    public Consumer<ApiCallCaptureInput> getCaptureOnSuccess() {
        return captureOnSuccess;
    }

    public Consumer<ApiCallCaptureInput> getCaptureOnError() {
        return captureOnError;
    }

    public Supplier<Boolean> getIsCanceledSupplier() {
        return isCanceledSupplier;
    }

    /**
     * @return z.B. "https://example.com/endpoint?key=..."
     */
    public abstract String getUri();

    /**
     * @return z.B. "POST", "GET", ...
     */
    public abstract String getHttpMethod();

    /**
     * @return Inhalt (String) für Body, z.B. JSON. Nur relevant bei POST/PUT.
     */
    public abstract String getBody();

    /**
     * Erzeugt die korrespondierende Response-Instanz
     * @param responseBody Inhalt aus der HTTP-Antwort
     */
    public abstract R createResponse(String responseBody);

    /**
     * Indikator, ob die Antwort binary ist (z.B. bei Datei-Downloads).
     */
    public boolean isBinaryResponse() {
        return false;
    }

    /**
     * Optional: Wenn die API Binary-Ergebnisse zurückgeben kann.
     */
    public R createResponse(byte[] responseBytes) {
        throw new UnsupportedOperationException("This request doesn't support binary responses.");
    }

    /**
     * Üblicherweise "application/json". Kann überschrieben werden.
     */
    public String getContentType() {
        return "application/json";
    }

    /**
     * Wenn wir binary request-bodies haben, kann man das hier überschreiben.
     */
    public byte[] getBodyBytes() {
        throw new UnsupportedOperationException("No binary body by default.");
    }

    /**
     * Legt einen zusätzlichen Header fest (z.B. "Authorization: Bearer ...").
     */
    public void setHeader(String name, String value) {
        additionalHeaders.put(name, value);
    }

    /**
     * Zugriff auf alle zusätzlich gesetzten Header (unveränderliche Map).
     */
    public Map<String, String> getAdditionalHeaders() {
        return Collections.unmodifiableMap(additionalHeaders);
    }
}

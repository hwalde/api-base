package de.entwicklertraining.api.base;

import java.net.http.HttpHeaders;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The HTTP exchange behind a transport-thrown API error: status code, response body and
 * response headers.
 * <p>
 * api-base's status-code exceptions are created by the transport with a composed message
 * only, which loses the parsed error body and the response headers a caller needs for
 * retry decisions and diagnostics (e.g. {@code Retry-After} or {@code X-RateLimit-*} on a
 * 429). A registered exception class that declares a public
 * {@code (String message, ApiErrorContext apiErrorContext)} constructor receives this
 * context when the transport throws it; every exception class api-base ships does. Read it
 * through {@link #of(Throwable)} or the {@code getApiErrorContext()} accessor of the
 * exception.
 *
 * @param statusCode     the HTTP status code of the failed exchange
 * @param responseBody   the raw response body, or {@code null} when none was read
 * @param responseHeaders the response headers, first value per name, never {@code null}
 */
public record ApiErrorContext(int statusCode,
                              String responseBody,
                              Map<String, String> responseHeaders) {

    /**
     * Creates an error context with an immutable, insertion-ordered header map.
     * {@code responseBody} may be {@code null}; {@code responseHeaders} may be
     * {@code null} and is then replaced by an empty map.
     */
    public ApiErrorContext {
        responseHeaders = responseHeaders == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(responseHeaders));
    }

    /**
     * Returns the value of the given response header (case-insensitive name match), or
     * {@code null} when the header was not sent. When a header was sent repeatedly, the
     * first value wins (see {@link #fromHeaders(HttpHeaders)}).
     *
     * @param name the header name, matched case-insensitively
     * @return the header value, or {@code null} when absent or {@code name} is {@code null}
     */
    public String header(String name) {
        if (name == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : responseHeaders.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Returns the error context a transport recorded on the given throwable, or
     * {@code null} when none is found. Walks the cause chain (capped at 20 hops), so a
     * transport exception wrapped by streaming or async layers - which often carries no
     * context of its own - still answers with the context of the wrapped error.
     *
     * @param throwable the exception to read the context from
     * @return the recorded context, or {@code null}
     */
    public static ApiErrorContext of(Throwable throwable) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 20; depth++) {
            if (current instanceof ApiErrorContextAware aware) {
                ApiErrorContext context = aware.getApiErrorContext();
                if (context != null) {
                    return context;
                }
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return null;
    }

    /**
     * Compact form for logs: the status code, the header names and the body length - the
     * body and the header values themselves are deliberately not dumped (an error body
     * and headers like {@code Set-Cookie} or {@code WWW-Authenticate} are not for logs).
     *
     * @return the compact description
     */
    @Override
    public String toString() {
        return "ApiErrorContext[statusCode=" + statusCode
                + ", responseHeaders=" + responseHeaders.keySet()
                + ", responseBody=(" + (responseBody == null ? 0 : responseBody.length()) + " chars)]";
    }

    /**
     * Flattens {@link HttpHeaders} into a first-value-per-name map. Headers are kept under
     * their original spelling; lookup is case-insensitive (see {@link #header(String)}).
     *
     * @param headers the headers of an HTTP response
     * @return the flattened map, never {@code null}
     */
    public static Map<String, String> fromHeaders(HttpHeaders headers) {
        Map<String, String> flat = new LinkedHashMap<>();
        if (headers == null) {
            return flat;
        }
        for (Map.Entry<String, java.util.List<String>> entry : headers.map().entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                flat.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        return flat;
    }
}

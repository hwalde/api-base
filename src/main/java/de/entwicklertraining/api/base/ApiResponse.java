package de.entwicklertraining.api.base;

/**
 * Abstraktion fuer eine generische API-Antwort, losgeloest von
 * spezifischen Dienst-Implementierungen.
 *
 * @param <Q> der zugehörige Request-Typ
 */
public abstract class ApiResponse<Q extends ApiRequest<?>> {

    private final Q request;

    protected ApiResponse(Q request) {
        this.request = request;
    }

    /**
     * Zugriff auf den Request, falls man z.B. Parameter erneut auswerten will.
     */
    public Q getRequest() {
        return request;
    }
}

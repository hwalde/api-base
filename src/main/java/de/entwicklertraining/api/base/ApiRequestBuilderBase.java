package de.entwicklertraining.api.base;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Gemeinsame abstrakte Basisklasse für alle Request-Builder,
 * die ein ApiRequest (oder eine Subklasse) erzeugen.
 *
 * 1. Enthält Felder und Setter für die "Basis-Angaben" wie maxExecutionTimeInSeconds
 * 2. Hat eine finale build()-Methode, die in zwei Phasen arbeitet:
 *      (a) newRequest(): Subklasse erzeugt das konkrete XYZRequest-Objekt
 *      (b) Basisklasse setzt "Basis-Felder" per Setter auf das frisch erstellte Request
 *
 * @param <B> Der konkrete Typ der Builder-Klasse (für Fluent-API).
 * @param <R> Die konkrete Request-Klasse, die gebaut werden soll.
 */
public abstract class ApiRequestBuilderBase<B extends ApiRequestBuilderBase<B, R>, R extends ApiRequest<?>> {

    protected int maxExecutionTimeInSeconds = 0;

    // Neue Felder zum Aktivieren des Capturings
    protected Consumer<ApiCallCaptureInput> captureOnSuccess;
    protected Consumer<ApiCallCaptureInput> captureOnError;

    protected Supplier<Boolean> isCanceledSupplier = () -> false;

    /** Fluent-Setter für die Shared-Felder. */
    @SuppressWarnings("unchecked")
    public B maxExecutionTimeInSeconds(int seconds) {
        this.maxExecutionTimeInSeconds = seconds;
        return (B) this;
    }

    /**
     * Aktiviert das Datenspeichern im Erfolgsfall (kein Exception-Abbruch).
     */
    @SuppressWarnings("unchecked")
    public B captureOnSuccess(Consumer<ApiCallCaptureInput> onSuccessConsumer) {
        this.captureOnSuccess = onSuccessConsumer;
        return (B) this;
    }

    /**
     * Aktiviert das Datenspeichern im Fehlerfall (wenn eine Exception auftritt).
     */
    @SuppressWarnings("unchecked")
    public B captureOnError(Consumer<ApiCallCaptureInput> onErrorConsumer) {
        this.captureOnError = onErrorConsumer;
        return (B) this;
    }

    @SuppressWarnings("unchecked")
    public B setCancelSupplier(Supplier<Boolean> isCanceledSupplier) {
        this.isCanceledSupplier = isCanceledSupplier;
        return (B) this;
    }

    /**
     * Finale Build-Methode, die "newRequest()" ruft und dann
     * automatisch alle Felder aus der Builder-Basis setzt.
     */
    public abstract R build();

    public abstract ApiResponse<R> execute();

    public abstract ApiResponse<R> executeWithoutExponentialBackoff();

}

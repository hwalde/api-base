package de.entwicklertraining.api.base.streaming;

import java.util.Map;

/**
 * Handler interface for processing streaming API responses.
 * 
 * <p>This interface defines the callback methods that will be invoked
 * as streaming data is received from the API. Implementations should
 * be thread-safe as methods may be called from different threads.
 * 
 * <p>The typical lifecycle is:
 * <ol>
 *   <li>Multiple calls to {@link #onData(Object)} as chunks arrive</li>
 *   <li>Possible calls to {@link #onMetadata(Map)} with additional information</li>
 *   <li>Either {@link #onComplete()} for successful completion or {@link #onError(Throwable)} for errors</li>
 * </ol>
 * 
 * @param <T> The type of data chunks expected in the streaming response
 */
public interface StreamingResponseHandler<T> {
    
    /**
     * Called when a new data chunk is received from the stream.
     * 
     * <p>This method is called for each piece of streamed data. The frequency
     * and size of chunks depends on the API and network conditions.
     * 
     * <p><strong>Thread Safety:</strong> This method may be called from multiple
     * threads concurrently. Implementations must be thread-safe.
     * 
     * @param data The data chunk received from the stream
     */
    void onData(T data);
    
    /**
     * Called when a new data chunk is received from the stream.
     * This is an alias for {@link #onData(Object)} to maintain compatibility
     * with different streaming implementations.
     * 
     * <p>Default implementation delegates to {@link #onData(Object)}.
     * 
     * @param chunk The data chunk received from the stream
     */
    default void onChunk(T chunk) {
        onData(chunk);
    }
    
    /**
     * Called when the stream completes successfully.
     * 
     * <p>This indicates that all data has been received and the stream
     * has been closed normally. No more {@link #onData(Object)} calls
     * will be made after this method is invoked.
     */
    void onComplete();
    
    /**
     * Called when an error occurs during streaming.
     * 
     * <p>This method is called if:
     * <ul>
     *   <li>Network connection is lost</li>
     *   <li>The server returns an error status</li>
     *   <li>Data parsing fails</li>
     *   <li>Any other streaming-related error occurs</li>
     * </ul>
     * 
     * <p>After this method is called, no more data will be received
     * and the stream is considered terminated.
     * 
     * @param throwable The error that occurred
     */
    void onError(Throwable throwable);
    
    /**
     * Called when metadata is received from the stream.
     * 
     * <p>Metadata can include information such as:
     * <ul>
     *   <li>Token usage statistics</li>
     *   <li>Processing time information</li>
     *   <li>Rate limiting headers</li>
     *   <li>Stream resumption tokens</li>
     * </ul>
     * 
     * <p>This method may be called multiple times during a stream's lifecycle,
     * or not at all if the API doesn't provide metadata.
     * 
     * @param metadata A map containing metadata key-value pairs
     */
    default void onMetadata(Map<String, Object> metadata) {
        // Default implementation does nothing
    }
    
    /**
     * Called when the stream connection is established.
     * 
     * <p>This is called once at the beginning of the stream, before any
     * {@link #onData(Object)} calls. It can be used for initialization
     * or logging purposes.
     * 
     * <p>Default implementation does nothing.
     */
    default void onStreamStart() {
        // Default implementation does nothing
    }
    
    /**
     * Called periodically to check if the stream should be cancelled.
     * 
     * <p>This method allows the handler to signal that it wants to
     * cancel the stream early. If this method returns true, the
     * streaming process will be aborted and {@link #onError(Throwable)}
     * will be called with a cancellation exception.
     * 
     * <p>Default implementation always returns false (never cancel).
     * 
     * @return true if the stream should be cancelled
     */
    default boolean shouldCancel() {
        return false;
    }
}
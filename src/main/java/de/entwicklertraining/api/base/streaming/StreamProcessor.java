package de.entwicklertraining.api.base.streaming;

/**
 * Interface for processing streaming response data in different formats.
 * 
 * <p>Stream processors handle the parsing and interpretation of streaming data
 * based on the specific format used by the API (SSE, JSON Lines, etc.).
 * They are responsible for:
 * <ul>
 *   <li>Parsing incoming data lines into structured chunks</li>
 *   <li>Detecting stream completion signals</li>
 *   <li>Extracting metadata from the stream</li>
 *   <li>Handling format-specific error conditions</li>
 * </ul>
 * 
 * @param <T> The type of data chunks produced by this processor
 */
public interface StreamProcessor<T> {
    
    /**
     * Processes a single line of streaming data.
     * 
     * <p>This method is called for each line received from the streaming response.
     * The processor should parse the line according to its format and invoke
     * appropriate methods on the response handler.
     * 
     * <p>The method should be resilient to malformed data and skip invalid lines
     * where possible, rather than throwing exceptions.
     * 
     * @param line The data line to process
     * @param handler The response handler to notify of parsed data
     * @throws StreamProcessingException if a fatal error occurs during processing
     */
    void processLine(String line, StreamingResponseHandler<T> handler) throws StreamProcessingException;
    
    /**
     * Checks if a given line indicates stream completion.
     * 
     * <p>Different streaming formats have different ways of signaling completion:
     * <ul>
     *   <li>SSE: "data: [DONE]"</li>
     *   <li>JSON Lines: a line containing completion metadata</li>
     *   <li>Custom: format-specific completion signals</li>
     * </ul>
     * 
     * @param line The line to check for completion
     * @return true if this line indicates the stream is complete
     */
    boolean isCompletionLine(String line);
    
    /**
     * Checks if a given line contains metadata rather than data.
     * 
     * <p>Some streaming formats include metadata lines that should be
     * processed differently from data lines. This method helps identify
     * such lines so they can be handled appropriately.
     * 
     * @param line The line to check for metadata
     * @return true if this line contains metadata
     */
    default boolean isMetadataLine(String line) {
        return false;
    }
    
    /**
     * Returns the streaming format handled by this processor.
     * 
     * @return The streaming format
     */
    StreamingFormat getFormat();
    
    /**
     * Resets the processor state for processing a new stream.
     * 
     * <p>This method is called before starting to process a new stream
     * and allows the processor to reset any internal state from previous
     * processing operations.
     * 
     * <p>Default implementation does nothing.
     */
    default void reset() {
        // Default implementation does nothing
    }
    
    /**
     * Exception thrown when a fatal error occurs during stream processing.
     */
    class StreamProcessingException extends Exception {
        /**
         * Constructs a new StreamProcessingException with the specified detail message.
         * 
         * @param message the detail message
         */
        public StreamProcessingException(String message) {
            super(message);
        }
        
        /**
         * Constructs a new StreamProcessingException with the specified detail message and cause.
         * 
         * @param message the detail message
         * @param cause the cause of the exception
         */
        public StreamProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
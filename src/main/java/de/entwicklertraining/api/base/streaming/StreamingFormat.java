package de.entwicklertraining.api.base.streaming;

/**
 * Enumeration of supported streaming response formats.
 * 
 * <p>Different APIs use different formats for streaming data. This enum
 * helps the streaming engine choose the appropriate parser and processing logic.
 */
public enum StreamingFormat {
    
    /**
     * Server-Sent Events format (text/event-stream).
     * 
     * <p>Uses the standard SSE format with:
     * <pre>
     * data: {json_content}
     * event: event_type
     * id: event_id
     * 
     * data: [DONE]
     * </pre>
     * 
     * <p>Commonly used by OpenAI, Anthropic, and similar AI APIs.
     */
    SERVER_SENT_EVENTS("text/event-stream"),
    
    /**
     * JSON Lines format (application/x-ndjson).
     * 
     * <p>Each line contains a complete JSON object:
     * <pre>
     * {"type": "chunk", "data": "content"}
     * {"type": "chunk", "data": "more content"}  
     * {"type": "done"}
     * </pre>
     * 
     * <p>Also known as newline-delimited JSON (NDJSON).
     */
    JSON_LINES("application/x-ndjson"),
    
    /**
     * Raw text streaming format.
     * 
     * <p>Simple text streaming without structured formatting.
     * Each line or chunk contains plain text data.
     */
    RAW_TEXT("text/plain"),
    
    /**
     * Custom streaming format.
     * 
     * <p>For APIs that use proprietary or non-standard streaming formats.
     * Requires custom implementation of StreamProcessor.
     */
    CUSTOM("application/octet-stream");
    
    private final String contentType;
    
    StreamingFormat(String contentType) {
        this.contentType = contentType;
    }
    
    /**
     * Returns the HTTP Content-Type header value for this streaming format.
     * 
     * @return The content type string
     */
    public String getContentType() {
        return contentType;
    }
    
    /**
     * Returns the Accept header value that should be sent to request this format.
     * 
     * @return The accept header value
     */
    public String getAcceptHeader() {
        return contentType;
    }
}
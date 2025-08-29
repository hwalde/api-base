package de.entwicklertraining.api.base.streaming;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Stream processor for Server-Sent Events (SSE) format.
 * 
 * <p>Handles the standard SSE format used by many streaming APIs:
 * <pre>
 * data: {"content": "Hello"}
 * event: chunk
 * id: event_123
 * 
 * data: {"content": " World"}
 * 
 * data: [DONE]
 * </pre>
 * 
 * <p>This processor:
 * <ul>
 *   <li>Parses SSE event format with data, event, and id fields</li>
 *   <li>Handles JSON parsing of data lines</li>
 *   <li>Detects completion signals ([DONE])</li>
 *   <li>Extracts metadata from event and id fields</li>
 * </ul>
 * 
 * @param <T> The type of data chunks to extract from JSON data
 */
public class SSEStreamProcessor<T> implements StreamProcessor<T> {
    
    private static final Logger logger = LoggerFactory.getLogger(SSEStreamProcessor.class);
    
    private final Class<T> dataType;
    private final DataExtractor<T> dataExtractor;
    
    // Current event state
    private String currentEventType = null;
    private String currentEventId = null;
    private final Map<String, Object> currentMetadata = new HashMap<>();
    
    /**
     * Creates a new SSE stream processor.
     * 
     * @param dataType The class type of data chunks to extract
     * @param dataExtractor Function to extract data from JSON objects
     */
    public SSEStreamProcessor(Class<T> dataType, DataExtractor<T> dataExtractor) {
        this.dataType = dataType;
        this.dataExtractor = dataExtractor;
    }
    
    @Override
    public void processLine(String line, StreamingResponseHandler<T> handler) throws StreamProcessingException {
        if (line == null || line.trim().isEmpty()) {
            // Empty line signals end of current event, send any accumulated data
            sendCurrentEvent(handler);
            return;
        }
        
        line = line.trim();
        
        if (line.startsWith("data:")) {
            processDataLine(line, handler);
        } else if (line.startsWith("event:")) {
            processEventLine(line);
        } else if (line.startsWith("id:")) {
            processIdLine(line);
        } else if (line.startsWith("retry:")) {
            processRetryLine(line);
        } else {
            // Unknown SSE field, ignore
            logger.debug("Ignoring unknown SSE field: {}", line);
        }
    }
    
    private void processDataLine(String line, StreamingResponseHandler<T> handler) throws StreamProcessingException {
        String data = line.substring(5).trim(); // Remove "data:" prefix
        
        if (isCompletionLine(line)) {
            handler.onComplete();
            return;
        }
        
        try {
            // Try to parse as JSON
            JSONObject jsonData = new JSONObject(data);
            T extractedData = dataExtractor.extract(jsonData);
            
            if (extractedData != null) {
                handler.onData(extractedData);
            }
            
            // Send any accumulated metadata
            if (!currentMetadata.isEmpty() || currentEventType != null || currentEventId != null) {
                Map<String, Object> metadata = new HashMap<>(currentMetadata);
                if (currentEventType != null) {
                    metadata.put("event_type", currentEventType);
                }
                if (currentEventId != null) {
                    metadata.put("event_id", currentEventId);
                }
                handler.onMetadata(metadata);
            }
            
        } catch (Exception e) {
            // If JSON parsing fails, treat as raw string data if possible
            if (dataType.isAssignableFrom(String.class)) {
                @SuppressWarnings("unchecked")
                T stringData = (T) data;
                handler.onData(stringData);
            } else {
                logger.warn("Failed to parse SSE data line as JSON: {} - {}", data, e.getMessage());
                // Don't throw exception, just skip malformed data
            }
        }
    }
    
    private void processEventLine(String line) {
        currentEventType = line.substring(6).trim(); // Remove "event:" prefix
    }
    
    private void processIdLine(String line) {
        currentEventId = line.substring(3).trim(); // Remove "id:" prefix
    }
    
    private void processRetryLine(String line) {
        try {
            String retryStr = line.substring(6).trim(); // Remove "retry:" prefix
            int retryMs = Integer.parseInt(retryStr);
            currentMetadata.put("retry_ms", retryMs);
        } catch (NumberFormatException e) {
            logger.warn("Invalid retry value in SSE: {}", line);
        }
    }
    
    private void sendCurrentEvent(StreamingResponseHandler<T> handler) {
        // Reset current event state
        currentEventType = null;
        currentEventId = null;
        currentMetadata.clear();
    }
    
    @Override
    public boolean isCompletionLine(String line) {
        if (!line.startsWith("data:")) {
            return false;
        }
        String data = line.substring(5).trim();
        return "[DONE]".equals(data) || "\"[DONE]\"".equals(data);
    }
    
    @Override
    public boolean isMetadataLine(String line) {
        return line.startsWith("event:") || line.startsWith("id:") || line.startsWith("retry:");
    }
    
    @Override
    public StreamingFormat getFormat() {
        return StreamingFormat.SERVER_SENT_EVENTS;
    }
    
    @Override
    public void reset() {
        currentEventType = null;
        currentEventId = null;
        currentMetadata.clear();
    }
    
    /**
     * Functional interface for extracting data from JSON objects.
     * 
     * @param <T> The type of data to extract
     */
    @FunctionalInterface
    public interface DataExtractor<T> {
        /**
         * Extracts data of type T from a JSON object.
         * 
         * @param jsonObject The JSON object to extract from
         * @return The extracted data, or null if no data to extract
         */
        T extract(JSONObject jsonObject);
    }
    
    /**
     * Common data extractors for typical use cases.
     */
    public static class CommonExtractors {
        
        /**
         * Extracts string content from a "content" field.
         */
        public static final DataExtractor<String> CONTENT_FIELD = json -> {
            if (json.has("content")) {
                return json.getString("content");
            }
            return null;
        };
        
        /**
         * Extracts string content from nested structure like choices[0].delta.content.
         */
        public static final DataExtractor<String> OPENAI_STYLE = json -> {
            try {
                if (json.has("choices")) {
                    var choices = json.getJSONArray("choices");
                    if (choices.length() > 0) {
                        var choice = choices.getJSONObject(0);
                        if (choice.has("delta")) {
                            var delta = choice.getJSONObject("delta");
                            if (delta.has("content")) {
                                return delta.getString("content");
                            }
                        }
                    }
                }
                return null;
            } catch (Exception e) {
                return null;
            }
        };
        
        /**
         * Returns the entire JSON object as a string.
         */
        public static final DataExtractor<String> RAW_JSON = JSONObject::toString;
    }
}
package de.entwicklertraining.api.base.streaming;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Stream processor for JSON Lines (NDJSON) format.
 * 
 * <p>Handles streaming responses where each line contains a complete JSON object:
 * <pre>
 * {"type": "chunk", "content": "Hello"}
 * {"type": "chunk", "content": " World"}
 * {"type": "metadata", "tokens": 42}
 * {"type": "done"}
 * </pre>
 * 
 * <p>This processor:
 * <ul>
 *   <li>Parses each line as a separate JSON object</li>
 *   <li>Extracts data based on configurable field names</li>
 *   <li>Detects completion based on type fields or custom logic</li>
 *   <li>Separates data from metadata based on message types</li>
 * </ul>
 * 
 * @param <T> The type of data chunks to extract from JSON objects
 */
public class JsonLinesStreamProcessor<T> implements StreamProcessor<T> {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonLinesStreamProcessor.class);
    
    private final Class<T> dataType;
    private final DataExtractor<T> dataExtractor;
    private final CompletionDetector completionDetector;
    private final MetadataDetector metadataDetector;
    
    /**
     * Creates a new JSON Lines stream processor with default detectors.
     * 
     * @param dataType The class type of data chunks to extract
     * @param dataExtractor Function to extract data from JSON objects
     */
    public JsonLinesStreamProcessor(Class<T> dataType, DataExtractor<T> dataExtractor) {
        this(dataType, dataExtractor, DefaultDetectors.TYPE_DONE, DefaultDetectors.TYPE_METADATA);
    }
    
    /**
     * Creates a new JSON Lines stream processor with custom detectors.
     * 
     * @param dataType The class type of data chunks to extract
     * @param dataExtractor Function to extract data from JSON objects
     * @param completionDetector Function to detect completion lines
     * @param metadataDetector Function to detect metadata lines
     */
    public JsonLinesStreamProcessor(Class<T> dataType, 
                                  DataExtractor<T> dataExtractor,
                                  CompletionDetector completionDetector,
                                  MetadataDetector metadataDetector) {
        this.dataType = dataType;
        this.dataExtractor = dataExtractor;
        this.completionDetector = completionDetector;
        this.metadataDetector = metadataDetector;
    }
    
    @Override
    public void processLine(String line, StreamingResponseHandler<T> handler) throws StreamProcessingException {
        if (line == null || line.trim().isEmpty()) {
            return; // Skip empty lines
        }
        
        line = line.trim();
        
        try {
            JSONObject jsonObject = new JSONObject(line);
            
            // Check for completion
            if (completionDetector.isCompletion(jsonObject)) {
                handler.onComplete();
                return;
            }
            
            // Check for metadata
            if (metadataDetector.isMetadata(jsonObject)) {
                Map<String, Object> metadata = extractMetadata(jsonObject);
                if (!metadata.isEmpty()) {
                    handler.onMetadata(metadata);
                }
                return;
            }
            
            // Extract data
            T extractedData = dataExtractor.extract(jsonObject);
            if (extractedData != null) {
                handler.onData(extractedData);
            }
            
        } catch (Exception e) {
            // If JSON parsing fails completely, try to handle as raw string
            if (dataType.isAssignableFrom(String.class) && isLikelyRawText(line)) {
                @SuppressWarnings("unchecked")
                T stringData = (T) line;
                handler.onData(stringData);
            } else {
                logger.warn("Failed to parse JSON line: {} - {}", line, e.getMessage());
                // Continue processing, don't throw exception for malformed lines
            }
        }
    }
    
    private Map<String, Object> extractMetadata(JSONObject jsonObject) {
        Map<String, Object> metadata = new HashMap<>();
        
        // Extract all fields except the type field as metadata
        for (String key : jsonObject.keySet()) {
            if (!"type".equals(key)) {
                metadata.put(key, jsonObject.get(key));
            }
        }
        
        return metadata;
    }
    
    private boolean isLikelyRawText(String line) {
        // Simple heuristic: if it doesn't start with { or [, might be raw text
        return !line.startsWith("{") && !line.startsWith("[");
    }
    
    @Override
    public boolean isCompletionLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }
        
        try {
            JSONObject jsonObject = new JSONObject(line.trim());
            return completionDetector.isCompletion(jsonObject);
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public boolean isMetadataLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }
        
        try {
            JSONObject jsonObject = new JSONObject(line.trim());
            return metadataDetector.isMetadata(jsonObject);
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public StreamingFormat getFormat() {
        return StreamingFormat.JSON_LINES;
    }
    
    /**
     * Functional interface for extracting data from JSON objects.
     * 
     * @param <T> The type of data to extract from JSON objects
     */
    @FunctionalInterface
    public interface DataExtractor<T> {
        /**
         * Extracts data from a JSON object.
         * 
         * @param jsonObject the JSON object to extract data from
         * @return the extracted data of type T
         */
        T extract(JSONObject jsonObject);
    }
    
    /**
     * Functional interface for detecting completion messages.
     */
    @FunctionalInterface
    public interface CompletionDetector {
        /**
         * Determines if the given JSON object represents a completion signal.
         * 
         * @param jsonObject the JSON object to check
         * @return true if this object indicates completion, false otherwise
         */
        boolean isCompletion(JSONObject jsonObject);
    }
    
    /**
     * Functional interface for detecting metadata messages.
     */
    @FunctionalInterface
    public interface MetadataDetector {
        /**
         * Determines if the given JSON object represents metadata.
         * 
         * @param jsonObject the JSON object to check
         * @return true if this object contains metadata, false otherwise
         */
        boolean isMetadata(JSONObject jsonObject);
    }
    
    /**
     * Common extractors and detectors for typical use cases.
     */
    public static class DefaultDetectors {
        
        /**
         * Default constructor for DefaultDetectors utility class.
         */
        public DefaultDetectors() {}
        
        /**
         * Detects completion based on type field being "done".
         */
        public static final CompletionDetector TYPE_DONE = json -> 
            json.has("type") && "done".equals(json.getString("type"));
        
        /**
         * Detects completion based on finish_reason field being present.
         */
        public static final CompletionDetector FINISH_REASON = json ->
            json.has("finish_reason") && !json.isNull("finish_reason");
        
        /**
         * Detects metadata based on type field being "metadata".
         */
        public static final MetadataDetector TYPE_METADATA = json ->
            json.has("type") && "metadata".equals(json.getString("type"));
        
        /**
         * Detects metadata based on presence of usage field.
         */
        public static final MetadataDetector USAGE_FIELD = json ->
            json.has("usage");
        
        /**
         * Never considers any line as metadata (all lines are data).
         */
        public static final MetadataDetector NONE = json -> false;
    }
    
    /**
     * Common data extractors for typical use cases.
     */
    public static class CommonExtractors {
        
        /**
         * Default constructor for CommonExtractors utility class.
         */
        public CommonExtractors() {}
        
        /**
         * Extracts string content from a "content" field.
         */
        public static final DataExtractor<String> CONTENT_FIELD = json -> {
            if (json.has("content") && !json.isNull("content")) {
                return json.getString("content");
            }
            return null;
        };
        
        /**
         * Extracts string content from a "data" field.
         */
        public static final DataExtractor<String> DATA_FIELD = json -> {
            if (json.has("data") && !json.isNull("data")) {
                return json.getString("data");
            }
            return null;
        };
        
        /**
         * Extracts string content from nested structure like delta.content.
         */
        public static final DataExtractor<String> DELTA_CONTENT = json -> {
            try {
                if (json.has("delta")) {
                    var delta = json.getJSONObject("delta");
                    if (delta.has("content") && !delta.isNull("content")) {
                        return delta.getString("content");
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
        
        /**
         * Extracts content based on message type, handling different field names.
         */
        public static final DataExtractor<String> SMART_CONTENT = json -> {
            // Try different common field names for content
            String[] contentFields = {"content", "data", "text", "message"};
            
            for (String field : contentFields) {
                if (json.has(field) && !json.isNull(field)) {
                    return json.getString(field);
                }
            }
            
            // Try nested delta.content
            if (json.has("delta")) {
                try {
                    var delta = json.getJSONObject("delta");
                    if (delta.has("content") && !delta.isNull("content")) {
                        return delta.getString("content");
                    }
                } catch (Exception e) {
                    // Continue to other extraction methods
                }
            }
            
            return null;
        };
    }
}
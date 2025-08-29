package de.entwicklertraining.api.base.streaming;

/**
 * Factory class for creating stream processors based on streaming format.
 * 
 * <p>This factory provides a centralized way to create appropriate stream processors
 * for different streaming formats, with sensible defaults for common use cases.
 */
public class StreamProcessorFactory {
    
    /**
     * Creates a stream processor for the specified format and data type.
     * 
     * <p>This method provides default configurations that work well for most
     * common streaming API scenarios.
     * 
     * @param <T> The type of data chunks to process
     * @param format The streaming format
     * @param dataType The class of the data type
     * @return A configured stream processor
     * @throws IllegalArgumentException if the format is not supported
     */
    public static <T> StreamProcessor<T> createProcessor(StreamingFormat format, Class<T> dataType) {
        switch (format) {
            case SERVER_SENT_EVENTS:
                return createSSEProcessor(dataType);
            case JSON_LINES:
                return createJsonLinesProcessor(dataType);
            case RAW_TEXT:
                return createRawTextProcessor(dataType);
            default:
                throw new IllegalArgumentException("Unsupported streaming format: " + format);
        }
    }
    
    /**
     * Creates an SSE processor with smart content extraction.
     * 
     * @param <T> The data type (typically String)
     * @param dataType The class of the data type
     * @return An SSE stream processor
     */
    @SuppressWarnings("unchecked")
    public static <T> SSEStreamProcessor<T> createSSEProcessor(Class<T> dataType) {
        if (String.class.isAssignableFrom(dataType)) {
            // Use OpenAI-style extraction for string data (most common case)
            return (SSEStreamProcessor<T>) new SSEStreamProcessor<>(
                String.class, 
                SSEStreamProcessor.CommonExtractors.OPENAI_STYLE
            );
        } else {
            // For other types, return raw JSON and let the client handle parsing
            return (SSEStreamProcessor<T>) new SSEStreamProcessor<>(
                String.class,
                SSEStreamProcessor.CommonExtractors.RAW_JSON
            );
        }
    }
    
    /**
     * Creates a JSON Lines processor with smart content extraction.
     * 
     * @param <T> The data type (typically String)  
     * @param dataType The class of the data type
     * @return A JSON Lines stream processor
     */
    @SuppressWarnings("unchecked")
    public static <T> JsonLinesStreamProcessor<T> createJsonLinesProcessor(Class<T> dataType) {
        if (String.class.isAssignableFrom(dataType)) {
            return (JsonLinesStreamProcessor<T>) new JsonLinesStreamProcessor<>(
                String.class,
                JsonLinesStreamProcessor.CommonExtractors.SMART_CONTENT,
                JsonLinesStreamProcessor.DefaultDetectors.TYPE_DONE,
                JsonLinesStreamProcessor.DefaultDetectors.TYPE_METADATA
            );
        } else {
            return (JsonLinesStreamProcessor<T>) new JsonLinesStreamProcessor<>(
                String.class,
                JsonLinesStreamProcessor.CommonExtractors.RAW_JSON,
                JsonLinesStreamProcessor.DefaultDetectors.TYPE_DONE,
                JsonLinesStreamProcessor.DefaultDetectors.USAGE_FIELD
            );
        }
    }
    
    /**
     * Creates a raw text processor.
     * 
     * @param <T> The data type (must be String)
     * @param dataType The class of the data type
     * @return A raw text stream processor
     */
    @SuppressWarnings("unchecked")
    public static <T> StreamProcessor<T> createRawTextProcessor(Class<T> dataType) {
        if (!String.class.isAssignableFrom(dataType)) {
            throw new IllegalArgumentException("Raw text processor only supports String data type");
        }
        
        return (StreamProcessor<T>) new RawTextStreamProcessor();
    }
    
    /**
     * Creates an SSE processor with custom data extraction logic.
     * 
     * @param <T> The data type
     * @param dataType The class of the data type
     * @param extractor Custom data extraction function
     * @return A configured SSE stream processor
     */
    public static <T> SSEStreamProcessor<T> createCustomSSEProcessor(
            Class<T> dataType, 
            SSEStreamProcessor.DataExtractor<T> extractor) {
        return new SSEStreamProcessor<>(dataType, extractor);
    }
    
    /**
     * Creates a JSON Lines processor with custom detection and extraction logic.
     * 
     * @param <T> The data type
     * @param dataType The class of the data type
     * @param extractor Custom data extraction function
     * @param completionDetector Custom completion detection function
     * @param metadataDetector Custom metadata detection function
     * @return A configured JSON Lines stream processor
     */
    public static <T> JsonLinesStreamProcessor<T> createCustomJsonLinesProcessor(
            Class<T> dataType,
            JsonLinesStreamProcessor.DataExtractor<T> extractor,
            JsonLinesStreamProcessor.CompletionDetector completionDetector,
            JsonLinesStreamProcessor.MetadataDetector metadataDetector) {
        return new JsonLinesStreamProcessor<>(dataType, extractor, completionDetector, metadataDetector);
    }
    
    /**
     * Simple stream processor for raw text streams.
     */
    private static class RawTextStreamProcessor implements StreamProcessor<String> {
        
        @Override
        public void processLine(String line, StreamingResponseHandler<String> handler) {
            if (line != null && !line.trim().isEmpty()) {
                if (isCompletionLine(line)) {
                    handler.onComplete();
                } else {
                    handler.onData(line);
                }
            }
        }
        
        @Override
        public boolean isCompletionLine(String line) {
            if (line == null) return false;
            String trimmed = line.trim();
            return "[DONE]".equals(trimmed) || 
                   "DONE".equals(trimmed) || 
                   "EOF".equals(trimmed) ||
                   trimmed.isEmpty();
        }
        
        @Override
        public StreamingFormat getFormat() {
            return StreamingFormat.RAW_TEXT;
        }
    }
}
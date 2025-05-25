package de.entwicklertraining.api.base;

import java.time.Instant;

/**
 * Represents the input data for capturing information about an API call.
 * This record contains timing information, success status, and any exception details
 * that occurred during the API call, along with the input and output data.
 *
 * @param startTime The timestamp when the API call started
 * @param endTime The timestamp when the API call completed
 * @param success Indicates whether the API call completed successfully
 * @param exceptionClass The fully qualified class name of any exception that occurred (nullable)
 * @param exceptionMessage The exception message if an exception occurred (nullable)
 * @param exceptionStacktrace The stack trace of any exception that occurred (nullable)
 * @param inputData The input data sent with the API call (typically JSON)
 * @param outputData The output data returned by the API call (typically JSON, nullable)
 */
public record ApiCallCaptureInput(Instant startTime,
                                  Instant endTime,
                                  boolean success,
                                  String exceptionClass,
                                  String exceptionMessage,
                                  String exceptionStacktrace,
                                  String inputData,
                                  String outputData) {
}

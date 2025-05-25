package de.entwicklertraining.api.base;

import java.time.Instant;

public record ApiCallCaptureInput(Instant startTime,
                                  Instant endTime,
                                  boolean success,
                                  String exceptionClass,
                                  String exceptionMessage,
                                  String exceptionStacktrace,
                                  String inputData,
                                  String outputData) {
}

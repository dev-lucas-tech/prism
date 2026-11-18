package com.prism.analyst.domain;

public class ReportParseException extends RuntimeException {

    public ReportParseException(String message) {
        super(message);
    }

    public ReportParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

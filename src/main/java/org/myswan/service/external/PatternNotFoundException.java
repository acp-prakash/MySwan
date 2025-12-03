package org.myswan.service.external;

/**
 * Exception thrown when a ticker is not found (404) at the eTrade pattern API
 */
public class PatternNotFoundException extends Exception {

    public PatternNotFoundException(String message) {
        super(message);
    }

    public PatternNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}


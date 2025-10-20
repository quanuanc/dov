package dev.cheng.dov.protocol;

/**
 * Signals issues while reading or interpreting the custom video container.
 */
public class VideoFormatException extends Exception {
    public VideoFormatException(String message) {
        super(message);
    }

    public VideoFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.image2playlist.exception;

public class ImageValidationException extends RuntimeException {

    private final String errorCode;

    public ImageValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

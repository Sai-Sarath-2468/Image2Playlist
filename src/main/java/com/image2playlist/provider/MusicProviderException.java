package com.image2playlist.provider;

public class MusicProviderException extends RuntimeException {

    private final String errorCode;

    public MusicProviderException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MusicProviderException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

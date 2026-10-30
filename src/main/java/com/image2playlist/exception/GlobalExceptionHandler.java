package com.image2playlist.exception;

import com.image2playlist.dto.ApiError;
import com.image2playlist.ocr.OcrException;
import com.image2playlist.provider.MusicProviderException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ImageValidationException.class)
    public ResponseEntity<ApiError> handleImageValidation(
            ImageValidationException exception, HttpServletRequest request
    ) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case "IMAGE_TOO_LARGE" -> HttpStatus.PAYLOAD_TOO_LARGE;
            case "UNSUPPORTED_IMAGE_TYPE" -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return error(status, exception.getErrorCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(ImageNotFoundException.class)
    public ResponseEntity<ApiError> handleImageNotFound(
            ImageNotFoundException exception, HttpServletRequest request
    ) {
        return error(HttpStatus.NOT_FOUND, "IMAGE_NOT_FOUND", "Image was not found", request);
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ApiError> handleSessionNotFound(
            SessionNotFoundException exception, HttpServletRequest request
    ) {
        return error(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session was not found", request);
    }

    @ExceptionHandler(SongCandidateNotFoundException.class)
    public ResponseEntity<ApiError> handleCandidateNotFound(
            SongCandidateNotFoundException exception, HttpServletRequest request
    ) {
        return error(HttpStatus.NOT_FOUND, "SONG_NOT_FOUND", "Song candidate was not found", request);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> handleValidation(
            ValidationException exception, HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage(), request);
    }

    @ExceptionHandler(OcrException.class)
    public ResponseEntity<ApiError> handleOcr(
            OcrException exception, HttpServletRequest request
    ) {
        logger.error("OCR request failed at {}", request.getRequestURI(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "OCR_FAILED", "Failed to extract text from image", request);
    }

    @ExceptionHandler(MusicProviderException.class)
    public ResponseEntity<ApiError> handleMusicProvider(
            MusicProviderException exception, HttpServletRequest request
    ) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case "MUSIC_AUTH_REQUIRED" -> HttpStatus.UNAUTHORIZED;
            case "MUSIC_PROVIDER_NOT_CONFIGURED" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "PLAYLIST_CREATION_FAILED" -> HttpStatus.BAD_GATEWAY;
            case "MUSIC_RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_GATEWAY;
        };
        logger.error("Music provider request failed with {}", exception.getErrorCode(), exception);
        return error(status, exception.getErrorCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(
            IllegalArgumentException exception, HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception exception, HttpServletRequest request
    ) {
        logger.error("Unhandled application error at {}", request.getRequestURI(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status, String code, String message, HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(
                new ApiError(code, message, Instant.now(), request.getRequestURI())
        );
    }
}

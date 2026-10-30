package com.image2playlist.dto;

import java.time.Instant;

public record ApiError(String error, String message, Instant timestamp, String path) {
}

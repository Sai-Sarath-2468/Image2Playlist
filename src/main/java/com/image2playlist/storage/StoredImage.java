package com.image2playlist.storage;

import java.nio.file.Path;

public record StoredImage(
        String imageId,
        String originalFileName,
        String storedFileName,
        String contentType,
        long size,
        Path path
) {
}

package com.image2playlist.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageStorageServiceTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesUsingServerGeneratedNameAndSafeExtension() throws Exception {
        ImageStorageService storage = new ImageStorageService(temporaryDirectory.toString());
        MockMultipartFile upload = new MockMultipartFile(
                "image", "..\\..\\evil.png", "image/png", new byte[]{1, 2, 3}
        );

        StoredImage stored = storage.saveImage(upload);

        assertTrue(UUID.fromString(stored.imageId()) != null);
        assertEquals(stored.imageId() + ".png", stored.storedFileName());
        assertEquals("evil.png", stored.originalFileName());
        assertEquals(temporaryDirectory.toAbsolutePath().normalize(), stored.path().getParent());
        assertTrue(storage.getPath(stored.storedFileName()).toFile().isFile());
    }
}

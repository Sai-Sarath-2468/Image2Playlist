package com.image2playlist.service;

import com.image2playlist.exception.ImageValidationException;
import com.image2playlist.storage.ImageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageServiceTests {

    @TempDir
    Path temporaryDirectory;

    private ImageService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new ImageService(new ImageStorageService(temporaryDirectory.toString()), null);
    }

    @Test
    void rejectsEmptyAndUnsupportedUploads() {
        assertEquals("INVALID_IMAGE", assertThrows(ImageValidationException.class,
                () -> service.validateImage(new MockMultipartFile("image", "empty.png", "image/png", new byte[0])))
                .getErrorCode());
        assertEquals("UNSUPPORTED_IMAGE_TYPE", assertThrows(ImageValidationException.class,
                () -> service.validateImage(new MockMultipartFile("image", "notes.txt", "text/plain", new byte[]{1})))
                .getErrorCode());
    }

    @Test
    void rejectsOversizedAndMismatchedImageContent() {
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        assertEquals("IMAGE_TOO_LARGE", assertThrows(ImageValidationException.class,
                () -> service.validateImage(new MockMultipartFile("image", "big.png", "image/png", oversized)))
                .getErrorCode());
        assertEquals("INVALID_IMAGE", assertThrows(ImageValidationException.class,
                () -> service.validateImage(new MockMultipartFile("image", "fake.png", "image/png", new byte[]{1, 2, 3})))
                .getErrorCode());
    }

    @Test
    void acceptsPngSignature() throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "image", "valid.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a}
        );
        service.validateImage(upload);
    }
}

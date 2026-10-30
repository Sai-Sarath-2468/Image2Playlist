package com.image2playlist.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

@Service
public class ImageStorageService {

    private final Path uploadDirectory;

    public ImageStorageService(@Value("${app.storage.upload-directory:uploads}") String directory)
            throws IOException {
        this.uploadDirectory = Path.of(directory).toAbsolutePath().normalize();
        Files.createDirectories(uploadDirectory);
    }

    public StoredImage saveImage(MultipartFile image) throws IOException {
        String originalName = safeOriginalName(image.getOriginalFilename());
        String extension = extensionFor(image.getContentType(), originalName);
        String imageId = UUID.randomUUID().toString();
        String storedFileName = imageId + extension;
        Path destination = uploadDirectory.resolve(storedFileName).normalize();

        if (!destination.getParent().equals(uploadDirectory)) {
            throw new IOException("Resolved image path is outside the upload directory");
        }

        try (var input = image.getInputStream()) {
            Files.copy(input, destination);
        }

        StoredImage storedImage = new StoredImage(
                imageId,
                originalName,
                storedFileName,
                image.getContentType(),
                image.getSize(),
                destination
        );
        return storedImage;
    }

    public Path getPath(String storedFileName) {
        Path path = uploadDirectory.resolve(storedFileName).normalize();
        if (!path.getParent().equals(uploadDirectory) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Stored image file is missing or invalid");
        }
        return path;
    }

    public void deleteImage(StoredImage image) throws IOException {
        Files.deleteIfExists(image.path());
    }

    private String extensionFor(String contentType, String originalName) {
        String extension = "";
        if (originalName != null) {
            int separator = Math.max(originalName.lastIndexOf('/'), originalName.lastIndexOf('\\'));
            String baseName = originalName.substring(separator + 1);
            int dot = baseName.lastIndexOf('.');
            if (dot >= 0) {
                extension = baseName.substring(dot).toLowerCase(Locale.ROOT);
            }
        }

        return switch (contentType == null ? "" : contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> extension.equals(".png") ? extension : ".png";
            case "image/jpeg" -> (extension.equals(".jpg") || extension.equals(".jpeg"))
                    ? extension : ".jpg";
            case "image/webp" -> extension.equals(".webp") ? extension : ".webp";
            default -> throw new IllegalArgumentException("Unsupported image content type");
        };
    }

    private String safeOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "image";
        }
        int separator = Math.max(originalName.lastIndexOf('/'), originalName.lastIndexOf('\\'));
        return originalName.substring(separator + 1).replaceAll("[\\p{Cntrl}]", "");
    }
}

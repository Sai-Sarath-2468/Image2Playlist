package com.image2playlist.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.image2playlist.storage.ImageStorageService;
import com.image2playlist.storage.StoredImage;
import com.image2playlist.persistence.entity.UploadedImageEntity;
import com.image2playlist.persistence.repository.UploadedImageRepository;
import com.image2playlist.exception.ImageNotFoundException;
import com.image2playlist.exception.ImageValidationException;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.io.InputStream;

@Service
public class ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private static final List<String> ALLOWED_TYPES = List.of(
            "image/png",
            "image/jpeg",
            "image/webp"
    );

    private final ImageStorageService imageStorageService;
    private final UploadedImageRepository uploadedImageRepository;

    public ImageService(
            ImageStorageService imageStorageService,
            UploadedImageRepository uploadedImageRepository
    )
    {
        this.imageStorageService = imageStorageService;
        this.uploadedImageRepository = uploadedImageRepository;
    }

    public StoredImage processImage(MultipartFile image) throws IOException {
        validateImage(image);

        StoredImage storedImage = imageStorageService.saveImage(image);
        try {
            uploadedImageRepository.save(new UploadedImageEntity(
                    storedImage.imageId(), storedImage.originalFileName(), storedImage.storedFileName(),
                    storedImage.contentType(), storedImage.size(), Instant.now()
            ));
            logger.info("Image upload accepted imageId={} contentType={} sizeBytes={}",
                    storedImage.imageId(), storedImage.contentType(), storedImage.size());
            return storedImage;
        } catch (RuntimeException exception) {
            imageStorageService.deleteImage(storedImage);
            throw exception;
        }
    }

    public StoredImage getStoredImage(String imageId) {
        final String canonicalImageId;
        try {
            canonicalImageId = UUID.fromString(imageId).toString();
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ImageNotFoundException(imageId);
        }
        UploadedImageEntity entity = uploadedImageRepository.findById(canonicalImageId)
                .orElseThrow(() -> new ImageNotFoundException(canonicalImageId));
        try {
            return new StoredImage(
                    entity.getImageId(), entity.getOriginalFileName(), entity.getStoredFileName(),
                    entity.getContentType(), entity.getSize(), imageStorageService.getPath(entity.getStoredFileName())
            );
        } catch (IllegalArgumentException exception) {
            throw new ImageNotFoundException(canonicalImageId);
        }
    }

    public void validateImage(MultipartFile image) throws IOException {

        if (image == null || image.isEmpty()) {
            throw new ImageValidationException("INVALID_IMAGE", "Image file is empty");
        }

        if (image.getSize() > MAX_FILE_SIZE) {
            throw new ImageValidationException("IMAGE_TOO_LARGE", "Image size must not exceed 10 MB");
        }

        String contentType = image.getContentType();

        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new ImageValidationException(
                    "UNSUPPORTED_IMAGE_TYPE", "Only PNG, JPEG and WEBP images are supported"
            );
        }

        if (!hasExpectedSignature(image, contentType)) {
            throw new ImageValidationException("INVALID_IMAGE", "Image content does not match its file type");
        }
    }

    private boolean hasExpectedSignature(MultipartFile image, String contentType) throws IOException {
        try (InputStream input = image.getInputStream()) {
            byte[] header = input.readNBytes(12);
            return switch (contentType) {
                case "image/png" -> header.length >= 8
                        && (header[0] & 0xff) == 0x89 && header[1] == 'P' && header[2] == 'N'
                        && header[3] == 'G' && (header[4] & 0xff) == 0x0d && (header[5] & 0xff) == 0x0a
                        && (header[6] & 0xff) == 0x1a && (header[7] & 0xff) == 0x0a;
                case "image/jpeg" -> header.length >= 3
                        && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
                        && (header[2] & 0xff) == 0xff;
                case "image/webp" -> header.length >= 12
                        && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                        && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
                default -> false;
            };
        }
    }
}

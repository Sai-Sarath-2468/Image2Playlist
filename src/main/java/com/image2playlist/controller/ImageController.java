package com.image2playlist.controller;

import com.image2playlist.dto.ImageUploadResponse;
import com.image2playlist.service.ImageService;
import com.image2playlist.storage.StoredImage;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService)
    {
        this.imageService = imageService;
    }

    @PostMapping("/upload")
    public ImageUploadResponse uploadImage(@RequestParam("image") MultipartFile image) throws IOException
    {

        StoredImage storedImage = imageService.processImage(image);

        return new ImageUploadResponse(
                "Image uploaded successfully",
                storedImage.imageId(),
                storedImage.originalFileName(),
                storedImage.contentType(),
                storedImage.size()
        );
    }
}

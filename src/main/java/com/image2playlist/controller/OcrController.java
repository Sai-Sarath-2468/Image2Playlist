package com.image2playlist.controller;

import com.image2playlist.dto.OcrResult;
import com.image2playlist.ocr.OcrService;
import com.image2playlist.service.ImageService;
import com.image2playlist.storage.StoredImage;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/images")
public class OcrController {

    private final ImageService imageService;
    private final OcrService ocrService;

    public OcrController(ImageService imageService, OcrService ocrService) {
        this.imageService = imageService;
        this.ocrService = ocrService;
    }

    @PostMapping("/{imageId}/ocr")
    public OcrResult extractText(@PathVariable String imageId) {
        StoredImage image = imageService.getStoredImage(imageId);
        return new OcrResult(image.imageId(), ocrService.extractText(image.path()));
    }
}

package com.image2playlist.ocr;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class OcrService {

    private static final Logger logger = LoggerFactory.getLogger(OcrService.class);

    private final ITesseract tesseract;

    public OcrService(@Value("${app.ocr.tessdata-path:}") String tessdataPath) {
        this.tesseract = new Tesseract();
        tesseract.setLanguage("eng");

        if (tessdataPath != null && !tessdataPath.isBlank()) {
            Path dataPath = Path.of(tessdataPath).toAbsolutePath().normalize();
            if (!Files.isDirectory(dataPath)) {
                throw new IllegalStateException("Configured Tesseract tessdata directory does not exist");
            }
            tesseract.setDatapath(dataPath.toString());
        }
    }

    public String extractText(Path imagePath) {

        try {
            if (imagePath == null || !Files.isRegularFile(imagePath)) {
                throw new OcrException("Image for OCR was not found");
            }

            logger.info("OCR started");
            String text = tesseract.doOCR(imagePath.toFile());
            logger.info("OCR completed characters={}", text.length());
            return text;

        } catch (TesseractException exception) {
            throw new OcrException("Failed to extract text from image", exception);
        } catch (OcrException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OcrException("Failed to extract text from image", exception);
        }
    }
}

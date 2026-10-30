package com.image2playlist.exception;

public class ImageNotFoundException extends RuntimeException {

    public ImageNotFoundException(String imageId) {
        super("Image not found: " + imageId);
    }
}

package com.image2playlist.dto;

public class ImageUploadResponse
{
    private String message;
    private String imageId;
    private String fileName;
    private String contentType;
    private long size;

    public ImageUploadResponse(
            String message,
            String imageId,
            String fileName,
            String contentType,
            long size
    ) {
        this.message = message;
        this.imageId = imageId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.size = size;
    }

    public String getMessage()
    {
        return message;
    }

    public String getImageId() {
        return imageId;
    }

    public String getFileName()
    {
        return fileName;
    }

    public String getContentType()
    {
        return contentType;
    }

    public long getSize()
    {
        return size;
    }
}

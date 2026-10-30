package com.image2playlist.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController
{
    @GetMapping("/health")
    public String health()
    {
        return "Image2Playlist backend working is fine";
    }
}

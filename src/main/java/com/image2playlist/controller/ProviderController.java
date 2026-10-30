package com.image2playlist.controller;

import com.image2playlist.provider.MusicProviderRegistry;
import com.image2playlist.provider.MusicProviderException;
import com.image2playlist.provider.MusicProviderType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

import java.util.List;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final MusicProviderRegistry providerRegistry;
    private final String appleMusicDeveloperToken;
    private final String storefront;

    public ProviderController(
            MusicProviderRegistry providerRegistry,
            @Value("${app.apple-music.developer-token:}") String appleMusicDeveloperToken,
            @Value("${app.apple-music.storefront:us}") String storefront
    ) {
        this.providerRegistry = providerRegistry;
        this.appleMusicDeveloperToken = appleMusicDeveloperToken;
        this.storefront = storefront;
    }

    @GetMapping
    public List<MusicProviderType> listProviders() {
        return providerRegistry.availableTypes();
    }

    @GetMapping("/apple-music/config")
    public Map<String, String> appleMusicConfig() {
        if (appleMusicDeveloperToken == null || appleMusicDeveloperToken.isBlank()) {
            throw new MusicProviderException(
                    "MUSIC_PROVIDER_NOT_CONFIGURED",
                    "Apple Music requires APPLE_MUSIC_DEVELOPER_TOKEN"
            );
        }
        return Map.of("developerToken", appleMusicDeveloperToken, "storefront", storefront);
    }

}

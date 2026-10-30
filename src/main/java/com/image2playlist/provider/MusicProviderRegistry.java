package com.image2playlist.provider;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class MusicProviderRegistry {

    private final Map<MusicProviderType, MusicProvider> providers = new EnumMap<>(MusicProviderType.class);

    public MusicProviderRegistry(List<MusicProvider> availableProviders) {
        for (MusicProvider provider : availableProviders) {
            if (providers.put(provider.type(), provider) != null) {
                throw new IllegalStateException("Multiple providers registered for " + provider.type());
            }
        }
    }

    public MusicProvider get(MusicProviderType type) {
        MusicProvider provider = providers.get(type);
        if (provider == null) {
            throw new MusicProviderException(
                    "MUSIC_PROVIDER_NOT_CONFIGURED", "Music provider is not available: " + type
            );
        }
        return provider;
    }

    public List<MusicProviderType> availableTypes() {
        return List.copyOf(providers.keySet());
    }
}

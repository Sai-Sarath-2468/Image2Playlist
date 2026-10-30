package com.image2playlist.provider.apple;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppleMusicHttpConfiguration {

    @Bean
    RestClient.Builder appleMusicRestClientBuilder() {
        return RestClient.builder();
    }
}

package com.digination.urlshortener.link;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app")
public record LinkProperties(
        String baseUrl,
        @DefaultValue("6") int codeLength,
        @DefaultValue("10") int maxGenerationAttempts) {
}

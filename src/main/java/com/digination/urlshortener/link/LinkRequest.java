package com.digination.urlshortener.link;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LinkRequest(
        @NotBlank(message = "url is required")
        @Size(max = 2048, message = "url must be at most 2048 characters")
        String url) {
}

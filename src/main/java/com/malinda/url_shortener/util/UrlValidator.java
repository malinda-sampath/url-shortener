package com.malinda.url_shortener.util;

import com.malinda.url_shortener.exceptions.InvalidUrlException;

import java.net.URI;

public class UrlValidator {
    public static void validateUrl(String longUrl) {
        if (longUrl == null || longUrl.isBlank()) {
            throw new InvalidUrlException("URL cannot be empty");
        }

        try {
            URI uri = URI.create(longUrl);

            String schema = uri.getScheme();

            if (schema == null || (!schema.equalsIgnoreCase("http") && !schema.equalsIgnoreCase("https")) || uri.getHost() == null) {
                throw new InvalidUrlException("Invalid URL format");
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidUrlException("Malformed URL");
        }
    }
}

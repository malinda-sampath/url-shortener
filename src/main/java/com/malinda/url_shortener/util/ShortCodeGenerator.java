package com.malinda.url_shortener.util;

import java.security.SecureRandom;

public class ShortCodeGenerator {
    private static final String CHARACTERS =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final SecureRandom secureRandom = new SecureRandom();

    public static String generateSHortCode() {
        StringBuilder shortCode = new StringBuilder();

        for (int i = 0; i < 6; i++) {
            int randomIndex = secureRandom.nextInt(CHARACTERS.length());
            shortCode.append(CHARACTERS.charAt(randomIndex));
        }

        return shortCode.toString();
    }
}

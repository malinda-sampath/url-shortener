package com.malinda.url_shortener.service.IMPL;

import com.malinda.url_shortener.service.UrlService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UrlServiceIMPL implements UrlService {

    private static final String CHARACTERS =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private final Map<String, String> urlMappings = new ConcurrentHashMap<>();

    @Override
    public String createShortUrl(String longUrl) {

        if (urlMappings.containsValue(longUrl)) {
            return "Url already exists";
        }


        System.out.println(longUrl);
        return "";
    }

    private int generateShortCode() {
        int randomIndex = secureRandom.nextInt(CHARACTERS.length());
    }
}

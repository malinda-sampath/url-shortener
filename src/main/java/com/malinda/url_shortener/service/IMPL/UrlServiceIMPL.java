package com.malinda.url_shortener.service.IMPL;

import com.malinda.url_shortener.dto.CreateUrlResponse;
import com.malinda.url_shortener.service.UrlService;
import com.malinda.url_shortener.util.ShortCodeGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UrlServiceIMPL implements UrlService {

    private final Map<String, String> urlMappings = new ConcurrentHashMap<>();

    @Value("${app.base-url}")
    private String baseUrl;

    @Override
    public CreateUrlResponse createShortUrl(String longUrl) {
        if (longUrl == null || longUrl.isBlank()) {
            throw new RuntimeException();
        }
        if (urlMappings.containsValue(longUrl)) {
            throw new RuntimeException();
        }

        String code = ShortCodeGenerator.generateShortCode();
        urlMappings.put(code, longUrl);
        return new CreateUrlResponse(code, baseUrl + code);
    }

    @Override
    public String redirectToOriginalUrl(String code) {
        if (code.isEmpty() || !urlMappings.containsKey(code)) {
            return "Invalid code";
        } else {
            return urlMappings.get(code);
        }
    }
}

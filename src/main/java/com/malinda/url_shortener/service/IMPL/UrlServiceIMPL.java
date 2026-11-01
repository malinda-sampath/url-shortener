package com.malinda.url_shortener.service.IMPL;

import com.malinda.url_shortener.service.UrlService;
import com.malinda.url_shortener.util.ShortCodeGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UrlServiceIMPL implements UrlService {

    private final Map<String, String> urlMappings = new ConcurrentHashMap<>();
    @Value("${app.base-url}")
    private String baseUrl;

    @Override
    public String createShortUrl(String longUrl) {

        if (urlMappings.containsValue(longUrl)) {
            return "Url already exists";
        } else {
            String code = ShortCodeGenerator.generateSHortCode();
            urlMappings.put(code, longUrl);
            return baseUrl + "/" + code;
        }
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

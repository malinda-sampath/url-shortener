package com.malinda.url_shortener.service.IMPL;

import com.malinda.url_shortener.service.UrlService;
import com.malinda.url_shortener.util.ShortCodeGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UrlServiceIMPL implements UrlService {

    @Value("${app.base-url}")
    private  String baseUrl;

    private final Map<String, String> urlMappings = new ConcurrentHashMap<>();

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
}

package com.malinda.url_shortener.service.IMPL;

import com.malinda.url_shortener.dto.CreateUrlResponse;
import com.malinda.url_shortener.dto.CreateUrlResult;
import com.malinda.url_shortener.exceptions.InvalidCodeException;
import com.malinda.url_shortener.service.UrlService;
import com.malinda.url_shortener.util.ShortCodeGenerator;
import com.malinda.url_shortener.util.UrlValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UrlServiceIMPL implements UrlService {

    private final Map<String, String> urlMappings = new ConcurrentHashMap<>();

    @Value("${app.base-url}")
    private String baseUrl;

    @Override
    public CreateUrlResult createShortUrl(String longUrl) {
        UrlValidator.validateUrl(longUrl);


        for (Map.Entry<String, String> entry : urlMappings.entrySet()) {

            if (entry.getValue().equals(longUrl)) {
                String existingCode = entry.getKey();

                return new CreateUrlResult(
                        new CreateUrlResponse(
                                existingCode,
                                baseUrl + existingCode),
                        false
                );
            }
        }

        String code = ShortCodeGenerator.generateShortCode();
        urlMappings.put(code, longUrl);
        return new CreateUrlResult(
                new CreateUrlResponse(
                        code,
                        baseUrl + code),
                true
        );
    }

    @Override
    public String redirectToOriginalUrl(String code) {
        if (code.isEmpty() || !urlMappings.containsKey(code)) {
            throw new InvalidCodeException("Unknown code");
        } else {
            return urlMappings.get(code);
        }
    }
}

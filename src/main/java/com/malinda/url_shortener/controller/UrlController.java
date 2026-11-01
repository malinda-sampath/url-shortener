package com.malinda.url_shortener.controller;

import com.malinda.url_shortener.service.UrlService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/")
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    @PostMapping("/url")
    public ResponseEntity<String> createShortUrl(@RequestBody String longUrl) {
        String response = urlService.createShortUrl(longUrl);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

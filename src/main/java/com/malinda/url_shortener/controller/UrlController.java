package com.malinda.url_shortener.controller;

import com.malinda.url_shortener.dto.CreateUrlRequest;
import com.malinda.url_shortener.service.UrlService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/")
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    @PostMapping("/url")
    public ResponseEntity<String> createShortUrl(@RequestBody CreateUrlRequest request) {
        String response = urlService.createShortUrl(request.longUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{code}")
    public  ResponseEntity<Void> redirectToOriginalUrl(@PathVariable String code) {
        String response = urlService.redirectToOriginalUrl(code);
        return  ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI.create(response))
                .build();
    }
}

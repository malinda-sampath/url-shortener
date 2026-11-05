package com.malinda.url_shortener.service;

import com.malinda.url_shortener.dto.CreateUrlResponse;

public interface UrlService {
    CreateUrlResponse createShortUrl(String longUrl);

    String redirectToOriginalUrl(String code);
}

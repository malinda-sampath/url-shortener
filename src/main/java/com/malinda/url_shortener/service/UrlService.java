package com.malinda.url_shortener.service;

import com.malinda.url_shortener.dto.CreateUrlResult;

public interface UrlService {
    CreateUrlResult createShortUrl(String longUrl);

    String redirectToOriginalUrl(String code);
}

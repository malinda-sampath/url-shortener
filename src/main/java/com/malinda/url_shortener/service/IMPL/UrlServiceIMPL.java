package com.malinda.url_shortener.service.IMPL;

import com.malinda.url_shortener.dto.CreateUrlResponse;
import com.malinda.url_shortener.dto.CreateUrlResult;
import com.malinda.url_shortener.entity.Link;
import com.malinda.url_shortener.exceptions.InvalidCodeException;
import com.malinda.url_shortener.repo.LinkRepository;
import com.malinda.url_shortener.service.UrlService;
import com.malinda.url_shortener.util.ShortCodeGenerator;
import com.malinda.url_shortener.util.UrlValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UrlServiceIMPL implements UrlService {

    private final LinkRepository linkRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    public UrlServiceIMPL(LinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    @Override
    public CreateUrlResult createShortUrl(String longUrl) {
        UrlValidator.validateUrl(longUrl);

        Optional<Link> existingLink = linkRepository.findByLongUrl(longUrl);

            if (existingLink.isPresent()) {

                String existingCode = existingLink.get().getCode();
                return new CreateUrlResult(
                        new CreateUrlResponse(
                                existingCode,
                                buildShortUrl(existingCode)),
                        false
                );
            }

        String code = ShortCodeGenerator.generateShortCode();
        Link link = new Link(code,longUrl);
        linkRepository.save(link);
        return new CreateUrlResult(
                new CreateUrlResponse(
                        code,
                        buildShortUrl(code)
                ),
                true
        );
    }

    @Override
    public String redirectToOriginalUrl(String code) {
        Optional<Link> link = linkRepository.findByCode(code);
        if (link.isEmpty()) {
            throw new InvalidCodeException("Unknown code");
        } else {
            return link.get().getLongUrl();
        }
    }

    private String buildShortUrl(String code) {
        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl
                : baseUrl + "/";

        return normalizedBaseUrl + code;
    }
}

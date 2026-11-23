package com.malinda.url_shortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class CreateUrlRequest {

    @NotBlank
    @Size(max = 2048)
    private String longUrl;
}

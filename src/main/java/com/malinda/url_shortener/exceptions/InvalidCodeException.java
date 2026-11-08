package com.malinda.url_shortener.exceptions;

public class InvalidCodeException extends RuntimeException{
    public InvalidCodeException(String message) {
        super(message);
    }
}

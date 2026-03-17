package ru.hgd.sdlc.common;

public class UnprocessableEntityException extends RuntimeException {
    public UnprocessableEntityException(String message) {
        super(message);
    }
}

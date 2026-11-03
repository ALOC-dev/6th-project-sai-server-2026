package com.aloc.sai.common.exception;

public record ErrorResponse(boolean success, String message, int code) {
    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(false, message, status);
    }
}

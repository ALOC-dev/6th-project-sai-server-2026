package com.aloc.sai.auth.dto;

public record TokenRefreshResponse(String accessToken, String refreshToken) {
}

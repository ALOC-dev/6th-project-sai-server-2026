package com.aloc.sai.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
        @NotBlank(message = "kakaoAccessToken은 필수입니다.")
        String kakaoAccessToken
) {
}

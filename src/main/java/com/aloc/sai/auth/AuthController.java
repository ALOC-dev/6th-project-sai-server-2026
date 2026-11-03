package com.aloc.sai.auth;

import com.aloc.sai.auth.dto.KakaoLoginRequest;
import com.aloc.sai.auth.dto.KakaoLoginResponse;
import com.aloc.sai.auth.dto.TokenRefreshRequest;
import com.aloc.sai.auth.dto.TokenRefreshResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/kakao")
    public KakaoLoginResponse loginWithKakao(@Valid @RequestBody KakaoLoginRequest request) {
        return authService.loginWithKakao(request.kakaoAccessToken());
    }

    @PostMapping("/refresh")
    public TokenRefreshResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }
}

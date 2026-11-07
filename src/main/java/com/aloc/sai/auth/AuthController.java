package com.aloc.sai.auth;

import com.aloc.sai.auth.dto.KakaoLoginRequest;
import com.aloc.sai.auth.dto.KakaoLoginResponse;
import com.aloc.sai.auth.dto.LogoutRequest;
import com.aloc.sai.auth.dto.TokenRefreshRequest;
import com.aloc.sai.auth.dto.TokenRefreshResponse;
import com.aloc.sai.security.LoginUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @LoginUserId Long userId,
            @Valid @RequestBody LogoutRequest request
    ) {
        authService.logout(userId, request.refreshToken());
    }
}

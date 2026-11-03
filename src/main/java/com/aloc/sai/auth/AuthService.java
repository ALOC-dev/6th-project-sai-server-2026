package com.aloc.sai.auth;

import com.aloc.sai.auth.dto.KakaoLoginResponse;
import com.aloc.sai.auth.dto.TokenRefreshResponse;
import com.aloc.sai.auth.kakao.KakaoClient;
import com.aloc.sai.auth.kakao.KakaoUserInfo;
import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.security.JwtTokenProvider;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserRepository;
import com.aloc.sai.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final KakaoClient kakaoClient;
    private final UserRepository userRepository;
    private final UserService userService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public KakaoLoginResponse loginWithKakao(String kakaoAccessToken) {
        KakaoUserInfo kakaoUser = kakaoClient.getUserInfo(kakaoAccessToken);

        User existingUser = userRepository.findByKakaoId(kakaoUser.kakaoId()).orElse(null);
        if (existingUser != null) {
            return issueTokens(existingUser, false);
        }

        User newUser;
        try {
            newUser = userService.registerKakaoUser(
                    kakaoUser.kakaoId(), kakaoUser.nickname(), kakaoUser.profileImageUrl());
        } catch (DataIntegrityViolationException e) {
            User concurrentlyRegisteredUser = userRepository.findByKakaoId(kakaoUser.kakaoId()).orElseThrow(() -> e);
            return issueTokens(concurrentlyRegisteredUser, false);
        }

        return issueTokens(newUser, true);
    }

    @Transactional
    public TokenRefreshResponse refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)
                || !"refresh".equals(jwtTokenProvider.getTokenType(refreshToken))
                || refreshTokenRepository.deleteByTokenHash(hash(refreshToken)) == 0) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "만료되었거나 폐기된 리프레시 토큰입니다.");
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String newRefreshToken = saveNewRefreshToken(userRepository.getReferenceById(userId));

        return new TokenRefreshResponse(jwtTokenProvider.createToken(userId), newRefreshToken);
    }

    @Transactional
    public void logout(Long userId, String refreshToken) {
        refreshTokenRepository.deleteByTokenHashAndUserId(hash(refreshToken), userId);
    }

    private KakaoLoginResponse issueTokens(User user, boolean isNewUser) {
        String accessToken = jwtTokenProvider.createToken(user.getUserId());
        String refreshToken = saveNewRefreshToken(user);

        return KakaoLoginResponse.of(accessToken, refreshToken, isNewUser, user);
    }

    private String saveNewRefreshToken(User user) {
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());

        refreshTokenRepository.save(new RefreshToken(
                user, hash(refreshToken), jwtTokenProvider.getExpiresAt(refreshToken)));

        return refreshToken;
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}

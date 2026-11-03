package com.aloc.sai.auth.dto;

import com.aloc.sai.user.User;

public record KakaoLoginResponse(
        String accessToken,
        String refreshToken,
        boolean isNewUser,
        boolean profileCompleted,
        UserSummary user
) {
    public static KakaoLoginResponse of(String accessToken, String refreshToken, boolean isNewUser, User user) {
        return new KakaoLoginResponse(
                accessToken,
                refreshToken,
                isNewUser,
                user.isProfileCompleted(),
                new UserSummary(user.getUserId(), user.getNickname(), user.getProfileImageUrl())
        );
    }

    public record UserSummary(Long userId, String nickname, String profileImageUrl) {
    }
}

package com.aloc.sai.user.dto;

import com.aloc.sai.user.User;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long userId,
        String nickname,
        String profileImageUrl,
        String statusMessage,
        String studentId,
        String department,
        boolean marketingAgreed,
        LocalDateTime marketingAgreedAt
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getUserId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getStatusMessage(),
                user.getStudentId(),
                user.getDepartment(),
                user.isMarketingAgreed(),
                user.getMarketingAgreedAt()
        );
    }
}

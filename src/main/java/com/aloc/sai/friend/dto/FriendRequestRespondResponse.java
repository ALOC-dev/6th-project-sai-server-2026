package com.aloc.sai.friend.dto;

import com.aloc.sai.friend.FriendRequest;
import com.aloc.sai.friend.FriendRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// friendshipId는 수락했을 때만 값이 있다.
public record FriendRequestRespondResponse(
        @Schema(description = "친구 요청 아이디", example = "10")
        Long friendRequestId,

        @Schema(description = "처리 후 상태 (ACCEPTED 또는 REJECTED)", example = "ACCEPTED")
        FriendRequestStatus requestStatus,

        @Schema(description = "수락/거절 시각", example = "2026-10-06T10:05:00")
        LocalDateTime respondedAt,

        @Schema(description = "생성된 친구관계 아이디 (수락일 때만, 거절이면 null)", example = "5", nullable = true)
        Long friendshipId
) {
    public static FriendRequestRespondResponse of(FriendRequest request, Long friendshipId) {
        return new FriendRequestRespondResponse(
                request.getFriendRequestId(),
                request.getRequestStatus(),
                request.getRespondedAt(),
                friendshipId
        );
    }
}

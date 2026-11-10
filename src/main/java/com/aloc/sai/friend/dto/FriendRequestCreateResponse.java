package com.aloc.sai.friend.dto;

import com.aloc.sai.friend.FriendRequest;
import com.aloc.sai.friend.FriendRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record FriendRequestCreateResponse(
        @Schema(description = "친구 요청 아이디", example = "10")
        Long friendRequestId,

        @Schema(description = "수신자 ID", example = "2")
        Long receiverId,

        @Schema(description = "요청 상태 (생성 직후 항상 PENDING)", example = "PENDING")
        FriendRequestStatus requestStatus,

        @Schema(description = "요청 일시", example = "2026-10-06T10:00:00")
        LocalDateTime createdAt
) {
    public static FriendRequestCreateResponse from(FriendRequest request) {
        return new FriendRequestCreateResponse(
                request.getFriendRequestId(),
                request.getReceiver().getUserId(),
                request.getRequestStatus(),
                request.getCreatedAt()
        );
    }
}

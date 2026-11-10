package com.aloc.sai.friend.dto;

import com.aloc.sai.friend.FriendRequest;
import com.aloc.sai.friend.FriendRequestStatus;

import java.time.LocalDateTime;

public record FriendRequestCreateResponse(
        Long friendRequestId,
        Long receiverId,
        FriendRequestStatus requestStatus,
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

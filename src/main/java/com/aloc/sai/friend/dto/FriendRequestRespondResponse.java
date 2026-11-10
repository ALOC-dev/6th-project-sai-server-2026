package com.aloc.sai.friend.dto;

import com.aloc.sai.friend.FriendRequest;
import com.aloc.sai.friend.FriendRequestStatus;

import java.time.LocalDateTime;

// friendshipId는 수락했을 때만 값이 있다.
public record FriendRequestRespondResponse(
        Long friendRequestId,
        FriendRequestStatus requestStatus,
        LocalDateTime respondedAt,
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

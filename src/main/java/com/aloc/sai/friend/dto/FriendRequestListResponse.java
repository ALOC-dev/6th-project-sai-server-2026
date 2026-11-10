package com.aloc.sai.friend.dto;

import com.aloc.sai.friend.FriendRequest;
import com.aloc.sai.friend.FriendRequestStatus;
import com.aloc.sai.user.User;

import java.time.LocalDateTime;
import java.util.List;

// received의 user는 요청자, sent의 user는 수신자다.
// 안쪽 record 이름은 API 문서의 스키마 이름이 되므로 다른 DTO와 겹치지 않게 짓는다.
public record FriendRequestListResponse(
        List<FriendRequestItem> received,
        List<FriendRequestItem> sent
) {
    public static FriendRequestListResponse of(List<FriendRequest> received, List<FriendRequest> sent) {
        return new FriendRequestListResponse(
                received.stream().map(request -> FriendRequestItem.of(request, request.getRequester())).toList(),
                sent.stream().map(request -> FriendRequestItem.of(request, request.getReceiver())).toList()
        );
    }

    public record FriendRequestItem(
            Long friendRequestId,
            FriendRequestUser user,
            FriendRequestStatus requestStatus,
            LocalDateTime createdAt
    ) {
        static FriendRequestItem of(FriendRequest request, User otherUser) {
            return new FriendRequestItem(
                    request.getFriendRequestId(),
                    FriendRequestUser.from(otherUser),
                    request.getRequestStatus(),
                    request.getCreatedAt()
            );
        }
    }

    public record FriendRequestUser(Long userId, String nickname) {
        static FriendRequestUser from(User user) {
            return new FriendRequestUser(user.getUserId(), user.getNickname());
        }
    }
}

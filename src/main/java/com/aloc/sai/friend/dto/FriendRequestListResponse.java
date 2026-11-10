package com.aloc.sai.friend.dto;

import com.aloc.sai.friend.FriendRequest;
import com.aloc.sai.friend.FriendRequestStatus;
import com.aloc.sai.user.User;

import java.time.LocalDateTime;
import java.util.List;

// received의 user는 요청자, sent의 user는 수신자다.
public record FriendRequestListResponse(
        List<Item> received,
        List<Item> sent
) {
    public static FriendRequestListResponse of(List<FriendRequest> received, List<FriendRequest> sent) {
        return new FriendRequestListResponse(
                received.stream().map(request -> Item.of(request, request.getRequester())).toList(),
                sent.stream().map(request -> Item.of(request, request.getReceiver())).toList()
        );
    }

    public record Item(
            Long friendRequestId,
            UserSummary user,
            FriendRequestStatus requestStatus,
            LocalDateTime createdAt
    ) {
        static Item of(FriendRequest request, User otherUser) {
            return new Item(
                    request.getFriendRequestId(),
                    UserSummary.from(otherUser),
                    request.getRequestStatus(),
                    request.getCreatedAt()
            );
        }
    }

    public record UserSummary(Long userId, String nickname) {
        static UserSummary from(User user) {
            return new UserSummary(user.getUserId(), user.getNickname());
        }
    }
}

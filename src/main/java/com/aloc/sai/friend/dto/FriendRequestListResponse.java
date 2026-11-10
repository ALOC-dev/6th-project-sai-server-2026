package com.aloc.sai.friend.dto;

import com.aloc.sai.friend.FriendRequest;
import com.aloc.sai.friend.FriendRequestStatus;
import com.aloc.sai.user.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

// received의 user는 요청자, sent의 user는 수신자다.
public record FriendRequestListResponse(
        @Schema(description = "내가 받은 대기 중인 요청 (user = 요청자), 최신순")
        List<Item> received,

        @Schema(description = "내가 보낸 대기 중인 요청 (user = 수신자), 최신순")
        List<Item> sent
) {
    public static FriendRequestListResponse of(List<FriendRequest> received, List<FriendRequest> sent) {
        return new FriendRequestListResponse(
                received.stream().map(request -> Item.of(request, request.getRequester())).toList(),
                sent.stream().map(request -> Item.of(request, request.getReceiver())).toList()
        );
    }

    @Schema(name = "FriendRequestItem")
    public record Item(
            @Schema(description = "친구 요청 아이디", example = "10")
            Long friendRequestId,

            @Schema(description = "상대 사용자")
            UserSummary user,

            @Schema(description = "요청 상태 (이 목록에서는 항상 PENDING)", example = "PENDING")
            FriendRequestStatus requestStatus,

            @Schema(description = "요청 일시", example = "2026-10-06T10:00:00")
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

    @Schema(name = "FriendRequestUser")
    public record UserSummary(
            @Schema(description = "상대 유저 ID", example = "3")
            Long userId,

            @Schema(description = "상대 닉네임", example = "서연")
            String nickname
    ) {
        static UserSummary from(User user) {
            return new UserSummary(user.getUserId(), user.getNickname());
        }
    }
}

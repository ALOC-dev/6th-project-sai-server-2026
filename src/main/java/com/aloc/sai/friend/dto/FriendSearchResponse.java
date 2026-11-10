package com.aloc.sai.friend.dto;

import com.aloc.sai.user.User;

// 닉네임 검색과 초대 링크 조회가 같은 응답을 쓴다. friendRequestId는 대기 중인 요청이 있을 때만 값이 있다.
public record FriendSearchResponse(
        Long userId,
        String nickname,
        FriendshipStatus friendshipStatus,
        Long friendRequestId
) {
    public static FriendSearchResponse of(User user, FriendshipStatus friendshipStatus, Long friendRequestId) {
        return new FriendSearchResponse(user.getUserId(), user.getNickname(), friendshipStatus, friendRequestId);
    }
}

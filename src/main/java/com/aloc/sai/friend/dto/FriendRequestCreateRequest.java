package com.aloc.sai.friend.dto;

import jakarta.validation.constraints.NotNull;

public record FriendRequestCreateRequest(
        @NotNull(message = "친구 요청을 받을 사용자를 선택해 주세요.")
        Long receiverId
) {
}

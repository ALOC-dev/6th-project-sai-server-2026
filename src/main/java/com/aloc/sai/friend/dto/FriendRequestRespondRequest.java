package com.aloc.sai.friend.dto;

import jakarta.validation.constraints.NotNull;

public record FriendRequestRespondRequest(
        @NotNull(message = "수락 또는 거절을 선택해 주세요.")
        FriendRequestAction action
) {
}

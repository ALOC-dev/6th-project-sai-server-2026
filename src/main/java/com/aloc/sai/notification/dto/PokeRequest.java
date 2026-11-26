package com.aloc.sai.notification.dto;

import jakarta.validation.constraints.NotNull;

public record PokeRequest(
        @NotNull(message = "targetFriendId는 필수입니다.")
        Long targetFriendId
) {
}

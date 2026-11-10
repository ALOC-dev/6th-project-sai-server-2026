package com.aloc.sai.friend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record FriendRequestRespondRequest(
        @Schema(description = "처리 동작", example = "ACCEPT", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "수락 또는 거절을 선택해 주세요.")
        FriendRequestAction action
) {
}

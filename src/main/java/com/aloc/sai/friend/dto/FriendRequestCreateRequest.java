package com.aloc.sai.friend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record FriendRequestCreateRequest(
        @Schema(description = "친구 요청을 받을 사용자 ID", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "친구 요청을 받을 사용자를 선택해 주세요.")
        Long receiverId
) {
}

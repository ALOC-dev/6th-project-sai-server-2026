package com.aloc.sai.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatUpdateRequest(
        @NotBlank
        @Size(max = 20)
        String chatRoomName
) {
}

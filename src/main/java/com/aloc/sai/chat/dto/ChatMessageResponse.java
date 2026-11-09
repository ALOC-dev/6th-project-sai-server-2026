package com.aloc.sai.chat.dto;

public record ChatMessageResponse(
        String code,
        String Message,
        Long chatRoomId
) {
}

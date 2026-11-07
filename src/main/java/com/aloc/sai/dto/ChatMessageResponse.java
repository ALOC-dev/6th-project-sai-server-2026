package com.aloc.sai.dto;

public record ChatMessageResponse(
        String code,
        String Message,
        Long chatRoomId
) {
}

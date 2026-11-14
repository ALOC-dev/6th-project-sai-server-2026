package com.aloc.sai.chat.dto;

public record ChatReadResponse(
        Long chatRoomId,
        Long lastReadMessageId,
        int unreadCount
) {
}

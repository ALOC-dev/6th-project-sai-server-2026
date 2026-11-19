package com.aloc.sai.chat.dto;

public record ChatRoomUpdateResponse(
        Long chatRoomId,
        ChatRoomListResponse.LastMessage lastMessage,
        int unreadCount
) {
}

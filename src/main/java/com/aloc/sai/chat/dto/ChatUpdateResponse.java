package com.aloc.sai.chat.dto;

import com.aloc.sai.chat.ChatRoom;

public record ChatUpdateResponse(
        Long chatRoomId,
        String chatRoomName
) {

    public static ChatUpdateResponse from(ChatRoom room){
        return new ChatUpdateResponse(
                room.getChatRoomId(),
                room.getChatRoomName()
        );
    }
}

package com.aloc.sai.chat;

import com.aloc.sai.chat.dto.ChatRoomResponse;

public record CreateChatRoomResult(ChatRoomResponse room, boolean created) {
}

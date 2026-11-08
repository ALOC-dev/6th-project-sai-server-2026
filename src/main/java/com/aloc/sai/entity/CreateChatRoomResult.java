package com.aloc.sai.entity;

import com.aloc.sai.dto.ChatRoomResponse;

public record CreateChatRoomResult(ChatRoomResponse room, boolean created) {
}

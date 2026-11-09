package com.aloc.sai.chat.dto;

import com.aloc.sai.chat.ChatRoomType;

import java.time.LocalDateTime;
import java.util.List;

public record CreateChatRoomRequest(
        ChatRoomType type,
        String chatRoomName,
        LocalDateTime departAt,
        Integer maxMembers,
        List<Long> inviteeIds
) {
}

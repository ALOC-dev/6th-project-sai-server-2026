package com.aloc.sai.dto;

import com.aloc.sai.entity.ChatRoomType;

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

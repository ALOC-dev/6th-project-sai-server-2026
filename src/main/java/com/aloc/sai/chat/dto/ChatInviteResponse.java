package com.aloc.sai.chat.dto;

import com.aloc.sai.chat.ChatRoomType;

import java.util.List;

public record ChatInviteResponse(
        Long chatRoomId,
        boolean converted,
        ChatRoomType type,
        List<Long> invitedUserIds,
        int memberCount
) {
}

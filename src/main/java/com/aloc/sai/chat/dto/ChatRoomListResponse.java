package com.aloc.sai.chat.dto;

import com.aloc.sai.chat.ChatRoom;
import com.aloc.sai.chat.ChatRoomStatus;
import com.aloc.sai.chat.ChatRoomType;
import com.aloc.sai.chat.MessageType;

import java.time.LocalDateTime;
import java.util.List;

public record ChatRoomListResponse(
        List<ChatRoomSummary> chatRooms
) {
    public record ChatRoomSummary(
            Long chatRoomId,
            String chatRoomName,        // DIRECT면 null
            ChatRoomType type,          // DIRECT / PUBLIC / PRIVATE
            ChatRoomStatus status,      // ACTIVE / CLOSED
            Integer memberCount,
            Opponent opponent,          // 상대방정보
            LastMessage lastMessage,    // 메시지가 없으면 null
            Integer unreadCount,
            LocalDateTime expiresAt
    ){
        public static ChatRoomSummary of(ChatRoom room, int memberCount, Opponent opponent,
                                         LastMessage lastMessage, int unreadCount){
            return new ChatRoomSummary(
                    room.getChatRoomId(),
                    room.getChatRoomName(),
                    room.getType(),
                    room.getStatus(),
                    memberCount,
                    opponent,
                    lastMessage,
                    unreadCount,
                    room.getCreatedAt().plusHours(24)
            );
        }
    }

    public record Opponent(
            Long userId,
            String nickname,
            String profileImageUrl
    ){}

    public record LastMessage(
            MessageType type,
            String message,
            LocalDateTime sendAt
    ){}
}

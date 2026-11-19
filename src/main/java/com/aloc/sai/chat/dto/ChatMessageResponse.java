package com.aloc.sai.chat.dto;

import com.aloc.sai.chat.ChatMessage;
import com.aloc.sai.chat.MessageType;
import com.aloc.sai.user.User;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long chatMessagesId,
        Long chatRoomId,
        MessageType type,
        String message,
        Sender sender,
        LocalDateTime sendAt,
        Long recommendationRequestsId,
        Long recommendationRouteId
) {
    public record Sender(
            Long userId,
            String nickname,
            String profileImageUrl
    ){}

    public static ChatMessageResponse from(ChatMessage m){
        User u=m.getSender();
        Sender sender=(u==null)? null:
                new Sender(u.getUserId(),u.getNickname(),u.getProfileImageUrl());

        return new ChatMessageResponse(
                m.getChatMessagesId(),
                m.getChatRoom().getChatRoomId(),
                m.getType(),
                m.getMessage(),
                sender,
                m.getSendAt(),
                m.getRecommendationRequestsId(),
                m.getRecommendationRouteId()
        );
    }

}

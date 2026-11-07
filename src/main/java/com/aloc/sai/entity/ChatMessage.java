package com.aloc.sai.entity;

import com.aloc.sai.dto.ChatMessageRequest;
import com.aloc.sai.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.awt.*;
import java.time.LocalDateTime;


@Entity
@Table(name = "chatMessage",indexes = @Index(columnList = "chatRoomId, chatMessageId"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long chatMessagesId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ChatRoomId",nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId")
    private User sender;

    @Column(length = 255)
    private String message;

    //추천
    private Long recommendationRequestsId;

    private Long recommendationRouteId;

    private LocalDateTime sendAt;

    @Enumerated(EnumType.STRING)
    private MessageType type;

    public static ChatMessage text(ChatRoom room, User sender,String message){
        ChatMessage m=new ChatMessage();
        m.chatRoom=room;
        m.sender=sender;
        m.message=message;
        m.type=MessageType.TEXT;
        m.sendAt=LocalDateTime.now();
        return m;
    }

    public static ChatMessage system(ChatRoom room, String message){
        ChatMessage m=new ChatMessage();
        m.chatRoom=room;
        m.message=message;
        m.type=MessageType.SYSTEM;
        m.sendAt=LocalDateTime.now();
        return m;
    }
}

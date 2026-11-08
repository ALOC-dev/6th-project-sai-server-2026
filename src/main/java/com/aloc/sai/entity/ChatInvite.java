package com.aloc.sai.entity;

import com.aloc.sai.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chatInvited")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatInvite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long chatInvitedId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chatRoomId", nullable = false)
    private ChatRoom chatRoom;

    private Long lastReadMessageId;

    public static ChatInvite of(User user, ChatRoom chatRoom){
        ChatInvite ci=new ChatInvite();
        ci.user=user;
        ci.chatRoom=chatRoom;
        return ci;
    }

    public void read(Long messageId){
        if(lastReadMessageId==null || messageId>lastReadMessageId){
            this.lastReadMessageId=messageId;
        }
    }
}

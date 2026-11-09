package com.aloc.sai.chat;

import com.aloc.sai.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chatRoom")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long chatRoomId;

    @Column(length = 20)
    private String chatRoomName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host")
    private User host;//DIRECT는 null

    private Integer maxMembers;//DIRECT=2, PUBLIC=입력값

    @Enumerated(EnumType.STRING)
    private ChatRoomType type;

    @Enumerated(EnumType.STRING)
    private ChatRoomStatus status;

    private LocalDateTime createdAt;

    public static ChatRoom create(ChatRoomType type, String name, User host, Integer maxMembers) {
        ChatRoom room = new ChatRoom();
        boolean direct = type == ChatRoomType.DIRECT;
        room.type = type;
        room.chatRoomName = direct ? null : name.strip();
        room.host = direct ? null : host;
        room.maxMembers = switch (type) {
            case DIRECT -> 2;
            case PUBLIC -> maxMembers;
            case PRIVATE -> null;
        };
        room.status = ChatRoomStatus.ACTIVE;
        room.createdAt = LocalDateTime.now();
        return room;
    }

    //room의 상태가 close or 24시간이 지났을 때 ->close
    public boolean isClosed(){
        return status==ChatRoomStatus.CLOSED || LocalDateTime.now().isAfter(createdAt.plusHours(24));
    }

    public void close(){
        this.status=ChatRoomStatus.CLOSED;
    }

    public LocalDateTime getExpiresAt(){
        return createdAt.plusHours(24);
    }

    public boolean isFull(long currentCount) {
        return maxMembers != null && currentCount >= maxMembers;
    }

}

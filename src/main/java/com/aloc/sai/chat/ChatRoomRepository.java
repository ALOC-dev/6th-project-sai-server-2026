package com.aloc.sai.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatRoomRepository extends JpaRepository<ChatRoom,Long> {
    @Query(
            """
            select  ci.chatRoom.chatRoomId
            from ChatInvite ci
            where ci.chatRoom.type=:type
                and ci.chatRoom.status=:status
                and ci.chatRoom.createdAt>:since
                and ci.user.userId in (:userA, :userB)
            group by ci.chatRoom.chatRoomId
            having count(ci)=2
    """)
    List<Long> findDirectRoomIds(
            @Param("userA") Long userA,
            @Param("userB") Long userB,
            @Param("type") ChatRoomType type,
            @Param("status") ChatRoomStatus status,
            @Param("since") LocalDateTime since
    );

    List<ChatRoom> findAllByHost_UserId(Long userId);

    //생성 후 24시간이 지난 방 종료
    @Modifying
    @Query("""
            update ChatRoom r
            set r.status = com.aloc.sai.chat.ChatRoomStatus.CLOSED
            where r.status = com.aloc.sai.chat.ChatRoomStatus.ACTIVE
                and r.createdAt <= :threshold
            """)
    int closeCreatedBefore(@Param("threshold") LocalDateTime threshold);
}

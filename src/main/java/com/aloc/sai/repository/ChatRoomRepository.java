package com.aloc.sai.repository;

import com.aloc.sai.entity.ChatRoom;
import com.aloc.sai.entity.ChatRoomStatus;
import com.aloc.sai.entity.ChatRoomType;
import org.springframework.data.jpa.repository.JpaRepository;
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
}

package com.aloc.sai.repository;

import com.aloc.sai.entity.ChatInvite;
import com.aloc.sai.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatInvitedRepository extends JpaRepository<ChatInvite,Long> {

    @Query(
            """
        select ci.user
        from ChatInvite ci
        where ci.chatRoom.chatRoomId=:chatRoomId
        order by ci.chatInvitedId
        """
    )
    List<User> findMembers(
            @Param("chatRoomId") Long ChatRoomId
    );
}

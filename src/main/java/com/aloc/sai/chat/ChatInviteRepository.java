package com.aloc.sai.chat;

import com.aloc.sai.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatInviteRepository extends JpaRepository<ChatInvite,Long> {

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

    @Query(
            """
        select ci
        from ChatInvite ci
        where ci.user.userId=:userId
        """
    )
    List<ChatInvite> findAllByUserId(@Param("userId") Long userId);

    long countByChatRoom_ChatRoomId(Long chatRoomId);

    // ChatInvitedRepository
    @Query("""
        select ci from ChatInvite ci
        join fetch ci.user
        where ci.chatRoom.chatRoomId = :roomId
          and ci.user.userId <> :userId
        """)
    Optional<ChatInvite> findOpponent(@Param("roomId") Long roomId, @Param("userId") Long userId);


    //참여자와 사용자 정보 쿼리 가져오기
    @Query("""
        select ci
        from ChatInvite ci
        join fetch ci.user
        where ci.chatRoom.chatRoomId = :chatRoomId
        """)
    List<ChatInvite> findAllWithUserByChatRoomId(@Param("chatRoomId") Long chatRoomId);

    Optional<ChatInvite> findByUser_UserIdAndChatRoom_ChatRoomId(Long userId, Long chatRoomId);


}

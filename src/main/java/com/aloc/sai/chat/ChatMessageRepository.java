package com.aloc.sai.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Optional<ChatMessage> findTopByChatRoom_ChatRoomIdOrderByChatMessagesIdDesc(Long roomId);

    //안 읽은 메시지 수 (lastReadMessageId보다 큰 메시지 중 지정한 타입 제외(SYSTEM 제외용))
    long countByChatRoom_ChatRoomIdAndChatMessagesIdGreaterThanAndTypeNot(
            Long roomId, Long readId, MessageType type);

    boolean existsByChatMessagesIdAndChatRoom_ChatRoomId(Long chatMessagesId, Long chatRoomId);

    //탈퇴한 사용자의 메시지는 남기고 보낸 사람만 비운다
    @Modifying
    @Query("update ChatMessage m set m.sender = null where m.sender.userId = :userId")
    void clearSender(@Param("userId") Long userId);

    //작은 ID메시지를 최신순으로
    @Query("""
        select m from ChatMessage m
        left join fetch m.sender
        where m.chatRoom.chatRoomId = :chatRoomId
          and (:cursor is null or m.chatMessagesId < :cursor)
        order by m.chatMessagesId desc
        """)
    List<ChatMessage> findPage(@Param("chatRoomId") Long chatRoomId,
                               @Param("cursor") Long cursor,
                               Pageable pageable);
}

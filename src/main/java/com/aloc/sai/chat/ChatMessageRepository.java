package com.aloc.sai.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Optional<ChatMessage> findTopByChatRoom_ChatRoomIdOrderByChatMessagesIdDesc(Long roomId);

    //채팅방에서 마지막으로 읽은 메시지
    long countByChatRoom_ChatRoomIdAndChatMessagesIdGreaterThan(Long roomId, Long readId);

    boolean existsByChatMessagesIdAndChatRoom_ChatRoomId(Long chatMessagesId, Long chatRoomId);

    //탈퇴한 사용자의 메시지는 남기고 보낸 사람만 비운다
    @Modifying
    @Query("update ChatMessage m set m.sender = null where m.sender.userId = :userId")
    void clearSender(@Param("userId") Long userId);
}

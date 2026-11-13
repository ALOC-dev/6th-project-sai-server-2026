package com.aloc.sai.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Optional<ChatMessage> findTopByChatRoomIdOrderByChatMessagesIdDesc(Long roomId);

    //채팅방에서 마지막으로 읽은 메시지
    long countByChatRoomIdAndChatMessagesIdGreaterThan(Long roomId, Long readId);

    boolean existsByChatMessagesIdAndChatRoomId(Long chatMessagesId, Long chatRoomId);
}

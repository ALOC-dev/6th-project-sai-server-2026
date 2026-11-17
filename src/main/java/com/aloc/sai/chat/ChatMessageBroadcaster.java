package com.aloc.sai.chat;

import com.aloc.sai.chat.dto.ChatMessageResponse;
import com.aloc.sai.chat.dto.ChatMessageSentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

//메시지를 방에 뿌리는 역할
@Component
@RequiredArgsConstructor
public class ChatMessageBroadcaster {
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageService chatMessageService;

    //메시지 저장 커밋 뒤에 실행됨
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void broadcast(ChatMessageSentEvent event){
        ChatMessageResponse payload=chatMessageService.getMessage(event.chatMessageId());
        messagingTemplate.convertAndSend("/sub/chat-rooms/"+payload.chatRoomId(),payload);
    }
}

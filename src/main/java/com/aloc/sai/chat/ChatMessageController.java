package com.aloc.sai.chat;

import com.aloc.sai.chat.dto.ChatErrorResponse;
import com.aloc.sai.chat.dto.ChatMessageRequest;
import com.aloc.sai.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatMessageController {
    private final ChatMessageService chatMessageService;

    //클라이언트->서버
    @MessageMapping("/chat-rooms/{chatRoomId}/messages")
    public void sendMessage(
            @DestinationVariable Long chatRoomId,
            ChatMessageRequest request,
            Principal principal
    ){
        Long userId=Long.valueOf(principal.getName());
        chatMessageService.send(userId,chatRoomId,request);
    }

    //service가 제공한 ApiException->보낸 사람의 /user/queue/errors로
    @MessageExceptionHandler(ApiException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ChatErrorResponse handleApiException(ApiException e, @DestinationVariable Long chatRoomId) {
        return ChatErrorResponse.of(e.getStatus().value(), e.getMessage(), chatRoomId);
    }

    //예상 못한 예외처리
    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ChatErrorResponse handleUnexpected(Exception e, @DestinationVariable Long chatRoomId) {
        log.error("채팅 메시지 처리 실패", e);
        return ChatErrorResponse.of(500, "서버 오류가 발생했습니다.", chatRoomId);
    }
}

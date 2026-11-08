package com.aloc.sai.controller;

import com.aloc.sai.dto.ChatRoomResponse;
import com.aloc.sai.dto.CreateChatRoomRequest;
import com.aloc.sai.entity.ChatRoom;
import com.aloc.sai.entity.CreateChatRoomResult;
import com.aloc.sai.service.ChatRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ChatRoom", description = "채팅방 API")
@RestController
@RequestMapping("/chat-rooms")
@RequiredArgsConstructor
public class ChatRoomcontroller {
    private final ChatRoomService chatRoomService;
    @Operation(summary = "채팅방 생성",
            description = "DIRECT(1:1) 또는 PUBLIC(공개 약속방)을 생성합니다. "
                    + "같은 상대와 이미 열린 1:1 방이 있으면 새로 만들지 않고 기존 방을 200으로 반환합니다.")
    @PostMapping
    public ResponseEntity<ChatRoomResponse> createChatRoom(
            Authentication authentication,
            @RequestBody CreateChatRoomRequest request
    ){
        Long userId=Long.valueOf(authentication.getName());
        CreateChatRoomResult result=chatRoomService.chatRoomcreate(userId, request);

        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.room());
    }
}

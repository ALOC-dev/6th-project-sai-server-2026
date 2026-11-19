package com.aloc.sai.chat;

import com.aloc.sai.chat.dto.ChatErrorResponse;
import com.aloc.sai.chat.dto.ChatMessageListResponse;
import com.aloc.sai.chat.dto.ChatMessageRequest;
import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "ChatMessage", description = "채팅 메시지 전송 STOMP API (에러는 /user/queue/errors로 전달)")
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatMessageController {
    private static final String ERROR_DESTINATION = "/queue/errors";
    private static final String UNEXPECTED_ERROR_MESSAGE = "서버 오류가 발생했습니다.";

    private static final String UNAUTHORIZED_EXAMPLE = """
            {"code": 401, "message": "인증이 필요합니다.", "chatRoomId": 12}""";
    private static final String NOT_CHAT_MEMBER_EXAMPLE = """
            {"code": 403, "message": "채팅방 참여자가 아닙니다.", "chatRoomId": 12}""";
    private static final String CHAT_ROOM_NOT_FOUND_EXAMPLE = """
            {"code": 404, "message": "채팅방을 찾을 수 없습니다.", "chatRoomId": 12}""";
    private static final String CHAT_ROOM_CLOSED_EXAMPLE = """
            {"code": 409, "message": "종료된 채팅방입니다.", "chatRoomId": 12}""";
    private static final String SERVER_ERROR_EXAMPLE = """
            {"code": 500, "message": "서버 오류가 발생했습니다.", "chatRoomId": 12}""";

    private final ChatMessageService chatMessageService;

    @Operation(summary = "메시지 전송 (STOMP)",
            description = "STOMP SEND /pub/chat-rooms/{chatRoomId}/messages 로 메시지를 보냅니다. "
                    + "채팅방 참여자만 보낼 수 있으며, 저장된 메시지는 해당 채팅방 구독자에게 브로드캐스트됩니다. "
                    + "실패하면 HTTP 응답 대신 보낸 사람의 /user/queue/errors로 ChatErrorResponse가 전달되므로, "
                    + "클라이언트는 연결 직후 /user/queue/errors를 구독해야 합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "전송 성공 (채팅방 구독자에게 메시지 브로드캐스트)"),
            @ApiResponse(responseCode = "400", description = "메시지 내용 오류",
                    content = @Content(schema = @Schema(implementation = ChatErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code": 400, "message": "메시지 내용을 입력해 주세요.", "chatRoomId": 12}"""))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ChatErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "채팅방 참여자가 아님",
                    content = @Content(schema = @Schema(implementation = ChatErrorResponse.class),
                            examples = @ExampleObject(value = NOT_CHAT_MEMBER_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "채팅방 없음",
                    content = @Content(schema = @Schema(implementation = ChatErrorResponse.class),
                            examples = @ExampleObject(value = CHAT_ROOM_NOT_FOUND_EXAMPLE))),
            @ApiResponse(responseCode = "409", description = "종료된 채팅방",
                    content = @Content(schema = @Schema(implementation = ChatErrorResponse.class),
                            examples = @ExampleObject(value = CHAT_ROOM_CLOSED_EXAMPLE))),
            @ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
                    content = @Content(schema = @Schema(implementation = ChatErrorResponse.class),
                            examples = @ExampleObject(value = SERVER_ERROR_EXAMPLE)))
    })
    @MessageMapping("/chat-rooms/{chatRoomId}/messages")
    public void sendMessage(
            @Parameter(description = "메시지를 보낼 채팅방 ID", example = "12") @DestinationVariable Long chatRoomId,
            @Payload ChatMessageRequest request,
            @Parameter(hidden = true) Principal principal
    ) {
        Long userId = Long.valueOf(principal.getName());
        chatMessageService.send(userId, chatRoomId, request);
    }

    // 서비스에서 던진 ApiException -> 보낸 사람의 /user/queue/errors로 전달
    @MessageExceptionHandler(ApiException.class)
    @SendToUser(destinations = ERROR_DESTINATION, broadcast = false)
    public ChatErrorResponse handleApiException(
            ApiException e,
            @DestinationVariable Long chatRoomId
    ) {
        return ChatErrorResponse.of(e.getStatus().value(), e.getMessage(), chatRoomId);
    }

    // 예상하지 못한 예외 -> 로그 남기고 500으로 전달
    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = ERROR_DESTINATION, broadcast = false)
    public ChatErrorResponse handleUnexpected(
            Exception e,
            @DestinationVariable Long chatRoomId
    ) {
        log.error("채팅 메시지 처리 실패 - chatRoomId: {}", chatRoomId, e);
        return ChatErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), UNEXPECTED_ERROR_MESSAGE, chatRoomId);
    }
}
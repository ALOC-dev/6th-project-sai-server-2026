package com.aloc.sai.chat;

import com.aloc.sai.chat.dto.ChatRoomResponse;
import com.aloc.sai.chat.dto.CreateChatRoomRequest;
import com.aloc.sai.chat.dto.CreateChatRoomResult;
import com.aloc.sai.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "채팅방 생성 성공"),
            @ApiResponse(responseCode = "200", description = "같은 상대와 열린 1:1 방이 이미 있어 기존 방 반환"),
            @ApiResponse(responseCode = "400", description = "요청 값 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "type 누락", value = """
                                            {"success": false, "message": "type은 필수입니다.", "code": 400}"""),
                                    @ExampleObject(name = "DIRECT 초대 인원", value = """
                                            {"success": false, "message": "DIRECT는 상대 1명을 초대해야 합니다.", "code": 400}"""),
                                    @ExampleObject(name = "채팅방 이름", value = """
                                            {"success": false, "message": "채팅방 이름은 1~20자여야 합니다.", "code": 400}"""),
                                    @ExampleObject(name = "약속 시간", value = """
                                            {"success": false, "message": "약속 시간은 현재 이후여야 합니다.", "code": 400}"""),
                                    @ExampleObject(name = "정원", value = """
                                            {"success": false, "message": "정원은 2명 이상이고 초대 인원(본인 포함)보다 커야 합니다.", "code": 400}""")
                            })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "인증이 필요합니다.", "code": 401}"""))),
            @ApiResponse(responseCode = "404", description = "본인 또는 초대할 사용자가 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "초대할 사용자를 찾을 수 없습니다.", "code": 404}""")))
    })
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

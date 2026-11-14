package com.aloc.sai.chat;

import com.aloc.sai.chat.dto.ChatInviteRequest;
import com.aloc.sai.chat.dto.ChatInviteResponse;
import com.aloc.sai.chat.dto.ChatReadRequest;
import com.aloc.sai.chat.dto.ChatReadResponse;
import com.aloc.sai.chat.dto.ChatRoomDetailResponse;
import com.aloc.sai.chat.dto.ChatRoomListResponse;
import com.aloc.sai.chat.dto.ChatRoomResponse;
import com.aloc.sai.chat.dto.ChatUpdateRequest;
import com.aloc.sai.chat.dto.ChatUpdateResponse;
import com.aloc.sai.chat.dto.CreateChatRoomRequest;
import com.aloc.sai.chat.dto.CreateChatRoomResult;
import com.aloc.sai.common.exception.ErrorResponse;
import com.aloc.sai.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ChatRoom", description = "채팅방 생성·목록·상세·이름 변경·나가기, 친구 초대, 읽음 처리 API")
@RestController
@RequestMapping("/api/v1/chat-rooms")
@RequiredArgsConstructor
public class ChatRoomcontroller {
    private static final String UNAUTHORIZED_EXAMPLE = """
            {"success": false, "message": "인증이 필요합니다.", "code": 401}""";
    private static final String CHAT_ROOM_NOT_FOUND_EXAMPLE = """
            {"success": false, "message": "채팅방을 찾을 수 없습니다.", "code": 404}""";
    private static final String NOT_CHAT_MEMBER_EXAMPLE = """
            {"success": false, "message": "채팅방 참여자가 아닙니다.", "code": 403}""";
    private static final String CHAT_ROOM_CLOSED_EXAMPLE = """
            {"success": false, "message": "종료된 채팅방입니다.", "code": 409}""";
    private static final String USER_NOT_FOUND_EXAMPLE = """
            {"success": false, "message": "사용자를 찾을 수 없습니다.", "code": 404}""";

    private final ChatRoomService chatRoomService;

    @Operation(summary = "채팅방 생성",
            description = "DIRECT(1:1), PUBLIC(공개 약속방), PRIVATE(비밀 약속방)을 생성합니다. 친구만 초대할 수 있습니다. "
                    + "같은 상대와 이미 열린 1:1 방이 있으면 새로 만들지 않고 기존 방을 200으로 반환합니다. "
                    + "PUBLIC 방은 모집 중(RECRUITING) 약속이 함께 생성됩니다. 채팅방은 생성 후 24시간 뒤 자동 종료됩니다.")
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
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "친구가 아닌 사용자를 초대함",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "친구만 채팅방에 초대할 수 있습니다.", "code": 403}"""))),
            @ApiResponse(responseCode = "404", description = "본인 또는 초대할 사용자가 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = USER_NOT_FOUND_EXAMPLE)))
    })
    @PostMapping
    public ResponseEntity<ChatRoomResponse> createChatRoom(
            @LoginUserId Long userId,
            @Valid @RequestBody CreateChatRoomRequest request
    ) {
        CreateChatRoomResult result = chatRoomService.chatRoomcreate(userId, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.room());
    }

    @Operation(summary = "내 채팅방 목록 조회",
            description = "내가 참여 중인 채팅방을 마지막 메시지 최신순으로 반환합니다. 메시지가 없는 방은 뒤로 갑니다. "
                    + "DIRECT 방은 상대 정보(opponent)를, 각 방은 안 읽은 메시지 수(unreadCount)를 함께 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (방이 없으면 빈 배열)"),
            @ApiResponse(responseCode = "400", description = "status 또는 type 값 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "채팅방 상태 값이 올바르지 않습니다.", "code": 400}"""))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE)))
    })
    @GetMapping
    public ChatRoomListResponse getChatRooms(
            @LoginUserId Long userId,
            @Parameter(description = "채팅방 상태 필터: ACTIVE(기본) / CLOSED / ALL", example = "ACTIVE")
            @RequestParam(required = false) String status,
            @Parameter(description = "채팅방 종류 필터: DIRECT / PUBLIC / PRIVATE (없으면 전체)", example = "DIRECT")
            @RequestParam(required = false) ChatRoomType type
    ) {
        return chatRoomService.getChatRoom(userId, status, type);
    }

    @Operation(summary = "채팅방 상세 조회",
            description = "채팅방 정보와 참여자 목록, 이 방의 약속을 반환합니다. 약속이 없으면 appointment는 null입니다. "
                    + "참여자만 조회할 수 있으며, PUBLIC 방은 방장의 친구면 참여 전에도 조회할 수 있습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "채팅방 참여자가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "채팅방 참여자만 볼 수 있습니다.", "code": 403}"""))),
            @ApiResponse(responseCode = "404", description = "채팅방 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = CHAT_ROOM_NOT_FOUND_EXAMPLE)))
    })
    @GetMapping("/{chatRoomId}")
    public ChatRoomDetailResponse getChatRoomDetail(
            @LoginUserId Long userId,
            @Parameter(description = "채팅방 ID", example = "12") @PathVariable Long chatRoomId
    ) {
        return chatRoomService.getChatRoomDetail(userId, chatRoomId);
    }

    @Operation(summary = "채팅방 이름 변경",
            description = "방장만 변경할 수 있습니다. DIRECT(1:1) 방은 이름이 없어 변경할 수 없습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(responseCode = "400", description = "1:1 방이거나 이름 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "1:1 채팅방은 이름을 변경할 수 없습니다.", "code": 400}"""))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "방장이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "방장만 채팅방 이름을 바꿀 수 있습니다.", "code": 403}"""))),
            @ApiResponse(responseCode = "404", description = "채팅방 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = CHAT_ROOM_NOT_FOUND_EXAMPLE))),
            @ApiResponse(responseCode = "409", description = "종료된 채팅방",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = CHAT_ROOM_CLOSED_EXAMPLE)))
    })
    @PatchMapping("/{chatRoomId}")
    public ChatUpdateResponse updateChatRoom(
            @LoginUserId Long userId,
            @Parameter(description = "채팅방 ID", example = "12") @PathVariable Long chatRoomId,
            @Valid @RequestBody ChatUpdateRequest request
    ) {
        return chatRoomService.updateChatRoom(userId, chatRoomId, request);
    }

    @Operation(summary = "채팅방 나가기",
            description = "내 참여 정보를 삭제합니다. 방장이 나가면 남은 참여자 중 한 명에게 방장이 넘어가고, "
                    + "마지막 참여자가 나가면 채팅방이 종료(CLOSED)됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "나가기 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "이미 나갔거나 참여자가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = NOT_CHAT_MEMBER_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "채팅방 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = CHAT_ROOM_NOT_FOUND_EXAMPLE)))
    })
    @DeleteMapping("/{chatRoomId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveChatRoom(
            @LoginUserId Long userId,
            @Parameter(description = "나갈 채팅방 ID", example = "12") @PathVariable Long chatRoomId
    ) {
        chatRoomService.leaveChatRoom(userId, chatRoomId);
    }

    @Operation(summary = "채팅방에 친구 초대",
            description = "PUBLIC / PRIVATE 방은 같은 방에 바로 추가합니다(200). "
                    + "DIRECT(1:1) 방에서 초대하면 기존 방은 그대로 두고, 기존 두 사람과 초대한 친구로 새 PRIVATE 방을 만듭니다(201, converted=true). "
                    + "이미 참여 중인 사람은 제외되며, PUBLIC 방은 모집 정원을 넘을 수 없습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "기존 방에 초대 완료"),
            @ApiResponse(responseCode = "201", description = "1:1 방에서 초대해 새 비밀 약속방 생성"),
            @ApiResponse(responseCode = "400", description = "초대할 친구가 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "초대할 친구를 선택해 주세요.", "code": 400}"""))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "내가 참여자가 아니거나 친구가 아닌 사용자를 초대함",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "참여자 아님", value = NOT_CHAT_MEMBER_EXAMPLE),
                                    @ExampleObject(name = "친구 아님", value = """
                                            {"success": false, "message": "친구만 초대할 수 있습니다.", "code": 403}""")
                            })),
            @ApiResponse(responseCode = "404", description = "채팅방 또는 초대할 사용자가 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "채팅방 없음", value = CHAT_ROOM_NOT_FOUND_EXAMPLE),
                                    @ExampleObject(name = "사용자 없음", value = USER_NOT_FOUND_EXAMPLE)
                            })),
            @ApiResponse(responseCode = "409", description = "종료된 채팅방이거나 모집 정원 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "종료된 방", value = CHAT_ROOM_CLOSED_EXAMPLE),
                                    @ExampleObject(name = "정원 초과", value = """
                                            {"success": false, "message": "모집 정원을 넘어 초대할 수 없습니다.", "code": 409}""")
                            }))
    })
    @PostMapping("/{chatRoomId}/invitations")
    public ResponseEntity<ChatInviteResponse> inviteFriends(
            @LoginUserId Long userId,
            @Parameter(description = "초대할 채팅방 ID", example = "15") @PathVariable Long chatRoomId,
            @Valid @RequestBody ChatInviteRequest request
    ) {
        ChatInviteResponse response = chatRoomService.inviteFriends(userId, chatRoomId, request);
        HttpStatus status = response.converted() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @Operation(summary = "읽음 처리",
            description = "채팅방에 들어왔을 때와, 채팅방을 보는 중 새 메시지를 받았을 때 호출합니다. "
                    + "저장된 값보다 작은 메시지 ID를 보내면 무시하고 기존 값을 유지합니다. "
                    + "안 읽은 수는 이 방에서 lastReadMessageId보다 큰 메시지 수입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "읽음 처리 성공"),
            @ApiResponse(responseCode = "400", description = "다른 방의 메시지 ID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "이 채팅방의 메시지가 아닙니다.", "code": 400}"""))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "채팅방 참여자가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = NOT_CHAT_MEMBER_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "채팅방 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = CHAT_ROOM_NOT_FOUND_EXAMPLE)))
    })
    @PatchMapping("/{chatRoomId}/read")
    public ChatReadResponse markAsRead(
            @LoginUserId Long userId,
            @Parameter(description = "채팅방 ID", example = "12") @PathVariable Long chatRoomId,
            @Valid @RequestBody ChatReadRequest request
    ) {
        return chatRoomService.markAsRead(userId, chatRoomId, request);
    }
}

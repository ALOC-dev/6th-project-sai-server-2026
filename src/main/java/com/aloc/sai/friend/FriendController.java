package com.aloc.sai.friend;

import com.aloc.sai.common.exception.ErrorResponse;
import com.aloc.sai.friend.dto.FriendListResponse;
import com.aloc.sai.friend.dto.FriendRequestCreateRequest;
import com.aloc.sai.friend.dto.FriendRequestCreateResponse;
import com.aloc.sai.friend.dto.FriendRequestListResponse;
import com.aloc.sai.friend.dto.FriendRequestRespondRequest;
import com.aloc.sai.friend.dto.FriendRequestRespondResponse;
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

@Tag(name = "Friend", description = "친구 요청·수락·거절, 친구 목록·삭제 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FriendController {
    private static final String UNAUTHORIZED_EXAMPLE = """
            {"success": false, "message": "인증이 필요합니다.", "code": 401}""";
    private static final String RETRY_LATER_EXAMPLE = """
            {"success": false, "message": "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.", "code": 409}""";

    private final FriendService friendService;

    @Operation(summary = "친구 요청 전송",
            description = "닉네임 검색이나 초대 링크로 찾은 사용자에게 친구 요청을 보냅니다. "
                    + "두 사용자 사이에는 방향과 관계없이 대기 중인 요청이 하나만 존재할 수 있습니다. "
                    + "상대가 먼저 보낸 요청이 있으면 409를 반환하며, 받은 요청에서 수락하도록 안내합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "요청 생성 성공"),
            @ApiResponse(responseCode = "400", description = "자기 자신에게 요청했거나 receiverId가 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "자기 자신에게는 친구 요청을 보낼 수 없습니다.", "code": 400}"""))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "수신자 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "사용자를 찾을 수 없습니다.", "code": 404}"""))),
            @ApiResponse(responseCode = "409", description = "이미 친구이거나 대기 중인 요청이 있음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "이미 친구", value = """
                                            {"success": false, "message": "이미 친구입니다.", "code": 409}"""),
                                    @ExampleObject(name = "내가 이미 보냄", value = """
                                            {"success": false, "message": "이미 친구 요청을 보냈습니다.", "code": 409}"""),
                                    @ExampleObject(name = "상대가 먼저 보냄", value = """
                                            {"success": false, "message": "상대가 보낸 친구 요청이 있습니다. 받은 요청에서 수락해 주세요.", "code": 409}"""),
                                    @ExampleObject(name = "동시 요청 충돌", value = RETRY_LATER_EXAMPLE)
                            }))
    })
    @PostMapping("/friend-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public FriendRequestCreateResponse sendRequest(
            @LoginUserId Long userId,
            @Valid @RequestBody FriendRequestCreateRequest request
    ) {
        return friendService.sendRequest(userId, request.receiverId());
    }

    @Operation(summary = "친구 요청 목록 조회",
            description = "나와 관련된 대기 중(PENDING) 요청을 받은 요청(received)과 보낸 요청(sent)으로 나눠 최신순으로 반환합니다. "
                    + "received의 user는 요청자, sent의 user는 수신자입니다. 없는 쪽은 빈 배열입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE)))
    })
    @GetMapping("/friend-requests")
    public FriendRequestListResponse getRequests(@LoginUserId Long userId) {
        return friendService.getRequests(userId);
    }

    @Operation(summary = "친구 요청 수락/거절",
            description = "내가 받은 대기 중인 요청만 처리할 수 있습니다. "
                    + "수락하면 친구 관계가 생기고 friendshipId를 반환하며, 거절하면 friendshipId는 null입니다. "
                    + "거절된 뒤에는 다시 요청할 수 있습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공"),
            @ApiResponse(responseCode = "400", description = "action 값이 없거나 ACCEPT/REJECT가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "수락 또는 거절을 선택해 주세요.", "code": 400}"""))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "본인이 수신자가 아닌 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "내가 받은 친구 요청만 처리할 수 있습니다.", "code": 403}"""))),
            @ApiResponse(responseCode = "404", description = "요청 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "친구 요청을 찾을 수 없습니다.", "code": 404}"""))),
            @ApiResponse(responseCode = "409", description = "이미 처리된 요청 (PENDING 아님) 또는 동시 처리 충돌",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "이미 처리됨", value = """
                                            {"success": false, "message": "이미 처리된 친구 요청입니다.", "code": 409}"""),
                                    @ExampleObject(name = "동시 처리 충돌", value = RETRY_LATER_EXAMPLE)
                            }))
    })
    @PatchMapping("/friend-requests/{friendRequestId}")
    public FriendRequestRespondResponse respond(
            @LoginUserId Long userId,
            @Parameter(description = "처리할 친구 요청 아이디", example = "10") @PathVariable Long friendRequestId,
            @Valid @RequestBody FriendRequestRespondRequest request
    ) {
        return friendService.respond(userId, friendRequestId, request.action());
    }

    @Operation(summary = "친구 목록 조회",
            description = "친구 목록과 각 친구의 오늘 수업 시간을 닉네임 가나다순으로 반환합니다. "
                    + "공강 여부와 남은 공강 시간은 FE가 todayClasses와 현재 시각으로 계산합니다. "
                    + "현재는 시간표 기능이 없어 todayClasses가 항상 빈 배열이고, available=true여도 전체 친구를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (친구가 없으면 빈 배열)"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE)))
    })
    @GetMapping("/friends")
    public FriendListResponse getFriends(
            @LoginUserId Long userId,
            @Parameter(description = "true면 요청 시각(KST) 기준 지금 공강인 친구만 반환 (오늘 수업이 없는 친구 포함)", example = "false")
            @RequestParam(defaultValue = "false") boolean available
    ) {
        return friendService.getFriends(userId, available);
    }

    @Operation(summary = "친구 삭제",
            description = "친구 관계를 삭제합니다. 양쪽 모두의 친구 목록에서 사라지며, 이후 다시 친구 요청을 보낼 수 있습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "친구 관계 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "친구 관계를 찾을 수 없습니다.", "code": 404}""")))
    })
    @DeleteMapping("/friends/{friendId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFriend(
            @LoginUserId Long userId,
            @Parameter(description = "삭제할 친구의 유저 ID", example = "2") @PathVariable Long friendId
    ) {
        friendService.deleteFriend(userId, friendId);
    }
}

package com.aloc.sai.notification;

import com.aloc.sai.common.exception.ErrorResponse;
import com.aloc.sai.notification.dto.NotificationListResponse;
import com.aloc.sai.notification.dto.NotificationReadAllRequest;
import com.aloc.sai.notification.dto.UnreadCountResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림 API")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationQueryService notificationQueryService;
    private final NotificationService notificationService;

    @Operation(summary = "알림 전체 읽음 처리",
            description = "알림 창 진입 시(또는 '모두 읽음' 버튼) 호출합니다. lastNotificationId에는 목록 응답의 첫 알림 id "
                    + "(notifications[0].notificationId)를 보냅니다. 그 id 이하의 안 읽은 알림만 읽음 처리하므로, "
                    + "목록을 받은 뒤 도착한 알림은 안 읽음으로 남습니다. 응답은 처리 후 남은 안 읽은 알림 수이며 배지 갱신에 씁니다. "
                    + "목록이 비어 있으면 호출하지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공 (읽음 처리할 알림이 없어도 200)"),
            @ApiResponse(responseCode = "400", description = "lastNotificationId 누락",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "lastNotificationId는 필수입니다.", "code": 400}"""))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "인증이 필요합니다.", "code": 401}""")))
    })
    @PatchMapping("/read-all")
    public UnreadCountResponse readAll(
            @LoginUserId Long userId,
            @Valid @RequestBody NotificationReadAllRequest request
    ) {
        return notificationService.markAllRead(userId, request.lastNotificationId());
    }

    @Operation(summary = "알림 목록 조회",
            description = "본인 알림을 최신순으로 조회합니다. 다음 페이지는 응답의 nextCursor를 cursor로 보내고, 마지막 페이지면 nextCursor는 null입니다. "
                    + "알림 문구는 notificationType과 actor로 FE가 만듭니다. FRIEND_REQUEST 알림에는 요청 처리 상태(requestStatus)가 들어가고, "
                    + "다른 타입은 null입니다. CHAT_INVITE는 방 상태를 확인하지 않으므로 입장 시 채팅 API의 409/403을 처리해야 합니다. "
                    + "이 API는 읽음 처리를 하지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "size 범위 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "size는 1 이상 50 이하여야 합니다.", "code": 400}"""))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "인증이 필요합니다.", "code": 401}""")))
    })
    @GetMapping
    public NotificationListResponse getNotifications(
            @LoginUserId Long userId,
            @Parameter(description = "이전 응답의 nextCursor (첫 요청은 생략)", example = "120") @RequestParam(required = false) Long cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50)", example = "20") @RequestParam(required = false) Integer size,
            @Parameter(description = "안 읽은 알림만 조회") @RequestParam(defaultValue = "false") boolean unreadOnly
    ) {
        return notificationQueryService.getNotifications(userId, cursor, size, unreadOnly);
    }

    @Operation(summary = "안 읽은 알림 수 조회",
            description = "홈 화면 알림 배지용. 본인의 안 읽은 알림 수를 반환합니다. 없으면 0입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "인증이 필요합니다.", "code": 401}""")))
    })
    @GetMapping("/unread-count")
    public UnreadCountResponse getUnreadCount(@LoginUserId Long userId) {
        return notificationQueryService.getUnreadCount(userId);
    }
}

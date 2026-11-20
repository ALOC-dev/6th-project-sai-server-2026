package com.aloc.sai.appointment;

import com.aloc.sai.appointment.dto.AppointmentConfirmRequest;
import com.aloc.sai.appointment.dto.AppointmentListResponse;
import com.aloc.sai.appointment.dto.AppointmentResponse;
import com.aloc.sai.appointment.dto.AppointmentUpdateRequest;
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
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Appointment", description = "약속 확정·변경·취소, 약속 목록 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AppointmentController {
    private static final String UNAUTHORIZED_EXAMPLE = """
            {"success": false, "message": "인증이 필요합니다.", "code": 401}""";
    private static final String CHAT_ROOM_NOT_FOUND_EXAMPLE = """
            {"success": false, "message": "채팅방을 찾을 수 없습니다.", "code": 404}""";
    private static final String NOT_CHAT_MEMBER_EXAMPLE = """
            {"success": false, "message": "채팅방 참여자가 아닙니다.", "code": 403}""";
    private static final String NOT_HOST_EXAMPLE = """
            {"success": false, "message": "방장만 약속을 정하거나 바꿀 수 있습니다.", "code": 403}""";
    private static final String PAST_TIME_EXAMPLE = """
            {"success": false, "message": "약속 시간은 현재 이후여야 합니다.", "code": 400}""";
    private static final String RETURN_BEFORE_DEPART_EXAMPLE = """
            {"success": false, "message": "끝나는 시각은 약속 시간 이후여야 합니다.", "code": 400}""";

    private final AppointmentService appointmentService;

    @Operation(summary = "약속 확정",
            description = "채팅방의 약속을 확정합니다. PUBLIC / PRIVATE 방은 방장만, DIRECT(1:1) 방은 두 사람 모두 확정할 수 있습니다. "
                    + "PUBLIC 방은 모집 중(RECRUITING) 약속이 확정으로 바뀌고, 약속이 없는 방(DIRECT, PRIVATE)은 새로 만들어집니다. "
                    + "취소된 약속도 다시 확정할 수 있습니다. 확정한 사람이 약속 주인(createdBy)이 되고, 방에 안내 메시지가 올라갑니다. "
                    + "추천 루트(recommendationRouteId)로 확정하는 기능은 추천 기능 구현 후 지원합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "확정 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "약속 시간 누락", value = """
                                            {"success": false, "message": "약속 시간을 입력해 주세요.", "code": 400}"""),
                                    @ExampleObject(name = "과거 시각", value = PAST_TIME_EXAMPLE),
                                    @ExampleObject(name = "끝나는 시각", value = RETURN_BEFORE_DEPART_EXAMPLE),
                                    @ExampleObject(name = "추천 루트", value = """
                                            {"success": false, "message": "추천 루트로 확정하는 기능은 아직 지원하지 않습니다.", "code": 400}""")
                            })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "채팅방 참여자가 아니거나 방장이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "참여자 아님", value = NOT_CHAT_MEMBER_EXAMPLE),
                                    @ExampleObject(name = "방장 아님", value = NOT_HOST_EXAMPLE)
                            })),
            @ApiResponse(responseCode = "404", description = "채팅방 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = CHAT_ROOM_NOT_FOUND_EXAMPLE))),
            @ApiResponse(responseCode = "409", description = "종료된 채팅방이거나 이미 확정된 약속",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "종료된 방", value = """
                                            {"success": false, "message": "종료된 채팅방입니다.", "code": 409}"""),
                                    @ExampleObject(name = "이미 확정", value = """
                                            {"success": false, "message": "이미 확정된 약속입니다. 변경은 약속 변경을 이용해 주세요.", "code": 409}""")
                            }))
    })
    @PostMapping("/chat-rooms/{chatRoomId}/appointment")
    public AppointmentResponse confirm(
            @LoginUserId Long userId,
            @Parameter(description = "약속을 확정할 채팅방 ID", example = "12") @PathVariable Long chatRoomId,
            @RequestBody AppointmentConfirmRequest request
    ) {
        return appointmentService.confirm(userId, chatRoomId, request);
    }

    @Operation(summary = "약속 변경·취소",
            description = "보낸 필드만 바뀝니다. 취소는 status에 CANCELED만 보냅니다. "
                    + "시간만 보내면 보내지 않은 쪽(departAt 또는 returnAt)은 기존 값을 유지합니다. "
                    + "모집 정원(maxMembers)은 모집 중(RECRUITING)일 때만 바꿀 수 있고, 2명 이상이면서 현재 인원 이상이어야 합니다. "
                    + "바뀐 내용마다 방에 안내 메시지가 올라갑니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경·취소 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "status 값", value = """
                                            {"success": false, "message": "status는 CANCELED만 보낼 수 있습니다.", "code": 400}"""),
                                    @ExampleObject(name = "바꿀 내용 없음", value = """
                                            {"success": false, "message": "바꿀 내용이 없습니다.", "code": 400}"""),
                                    @ExampleObject(name = "과거 시각", value = PAST_TIME_EXAMPLE),
                                    @ExampleObject(name = "끝나는 시각", value = RETURN_BEFORE_DEPART_EXAMPLE),
                                    @ExampleObject(name = "약속 시간 미정", value = """
                                            {"success": false, "message": "약속 시간을 먼저 정해 주세요.", "code": 400}"""),
                                    @ExampleObject(name = "모집 중 아님", value = """
                                            {"success": false, "message": "모집 중인 약속만 정원을 바꿀 수 있습니다.", "code": 400}"""),
                                    @ExampleObject(name = "정원", value = """
                                            {"success": false, "message": "정원은 2명 이상이고 현재 인원(3명) 이상이어야 합니다.", "code": 400}""")
                            })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "채팅방 참여자가 아니거나 방장이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "참여자 아님", value = NOT_CHAT_MEMBER_EXAMPLE),
                                    @ExampleObject(name = "방장 아님", value = NOT_HOST_EXAMPLE)
                            })),
            @ApiResponse(responseCode = "404", description = "채팅방 또는 약속이 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "채팅방 없음", value = CHAT_ROOM_NOT_FOUND_EXAMPLE),
                                    @ExampleObject(name = "약속 없음", value = """
                                            {"success": false, "message": "약속을 찾을 수 없습니다.", "code": 404}""")
                            })),
            @ApiResponse(responseCode = "409", description = "이미 취소된 약속",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "이미 취소된 약속입니다.", "code": 409}""")))
    })
    @PatchMapping("/chat-rooms/{chatRoomId}/appointment")
    public AppointmentResponse update(
            @LoginUserId Long userId,
            @Parameter(description = "채팅방 ID", example = "12") @PathVariable Long chatRoomId,
            @RequestBody AppointmentUpdateRequest request
    ) {
        return appointmentService.update(userId, chatRoomId, request);
    }

    @Operation(summary = "약속 목록 (예정된 약속 + 친구들이 모집 중인 약속)",
            description = "약속 탭 화면을 한 번에 조회합니다. "
                    + "upcoming: 내가 참여 중인 방의 확정(CONFIRMED)·모집 중(RECRUITING) 약속 중 아직 시작 전인 것 (약속 시간 빠른 순, 시간 미정은 뒤로). "
                    + "recruitingByFriends: 친구가 방장인 열린 PUBLIC 방의 모집 중 약속 중 내가 아직 참여하지 않았고 정원이 남은 것. "
                    + "항목을 누르면 chatRoomId로 채팅방에 이동합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (없으면 빈 배열)"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE)))
    })
    @GetMapping("/appointments")
    public AppointmentListResponse getAppointments(@LoginUserId Long userId) {
        return appointmentService.getAppointments(userId);
    }
}
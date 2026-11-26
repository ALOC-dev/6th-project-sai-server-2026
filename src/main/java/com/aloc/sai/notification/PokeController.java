package com.aloc.sai.notification;

import com.aloc.sai.common.exception.ErrorResponse;
import com.aloc.sai.notification.dto.PokeRequest;
import com.aloc.sai.notification.dto.PokeResponse;
import com.aloc.sai.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림 API")
@RestController
@RequestMapping("/api/v1/pokes")
@RequiredArgsConstructor
public class PokeController {
    private final PokeService pokeService;

    @Operation(summary = "찌르기 전송",
            description = "친구에게 찌르기를 보냅니다. 받은 친구에게 푸시가 가고 알림 창에 POKE 알림이 기록됩니다(수락·거절 같은 액션 없음). "
                    + "같은 친구에게는 10분에 한 번만 보낼 수 있습니다. 친구가 수업 중인지는 서버에서 확인하지 않습니다. "
                    + "연타로 중복 전송되지 않도록 요청 중에는 버튼을 비활성화해 주세요.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "전송 성공"),
            @ApiResponse(responseCode = "400", description = "targetFriendId 누락 또는 본인에게 전송",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "누락", value = """
                                            {"success": false, "message": "targetFriendId는 필수입니다.", "code": 400}"""),
                                    @ExampleObject(name = "본인", value = """
                                            {"success": false, "message": "자기 자신은 찌를 수 없습니다.", "code": 400}""")
                            })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "인증이 필요합니다.", "code": 401}"""))),
            @ApiResponse(responseCode = "403", description = "친구가 아님(없는 사용자 포함)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "친구에게만 찌르기를 보낼 수 있습니다.", "code": 403}"""))),
            @ApiResponse(responseCode = "429", description = "10분 안에 같은 친구에게 다시 전송",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "같은 친구에게는 10분 후에 다시 찌를 수 있습니다.", "code": 429}""")))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PokeResponse poke(
            @LoginUserId Long userId,
            @Valid @RequestBody PokeRequest request
    ) {
        return pokeService.poke(userId, request.targetFriendId());
    }
}

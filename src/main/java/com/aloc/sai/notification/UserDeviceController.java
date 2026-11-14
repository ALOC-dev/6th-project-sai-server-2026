package com.aloc.sai.notification;

import com.aloc.sai.common.exception.ErrorResponse;
import com.aloc.sai.notification.dto.DeviceRegisterRequest;
import com.aloc.sai.notification.dto.DeviceRegisterResult;
import com.aloc.sai.notification.dto.DeviceResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "푸시 기기 등록 API")
@RestController
@RequestMapping("/api/v1/users/me/devices")
@RequiredArgsConstructor
public class UserDeviceController {
    private final UserDeviceService userDeviceService;

    @Operation(summary = "푸시 기기 등록",
            description = "앱 실행·로그인 시 Expo 푸시 토큰을 등록합니다. 같은 토큰이 이미 있으면 주인·플랫폼·갱신 시각을 바꾸고(200), "
                    + "없으면 새로 등록합니다(201). 같은 기기에서 다른 계정으로 로그인하면 토큰이 새 계정으로 옮겨 갑니다. "
                    + "로그아웃 때 pushToken을 함께 보내면 등록이 해제됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "기존 토큰 갱신"),
            @ApiResponse(responseCode = "201", description = "신규 기기 등록"),
            @ApiResponse(responseCode = "400", description = "pushToken 누락·형식 오류 또는 platform 값 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "pushToken 누락", value = """
                                            {"success": false, "message": "pushToken은 필수입니다.", "code": 400}"""),
                                    @ExampleObject(name = "pushToken 형식", value = """
                                            {"success": false, "message": "Expo 푸시 토큰 형식이 아닙니다.", "code": 400}"""),
                                    @ExampleObject(name = "platform 누락", value = """
                                            {"success": false, "message": "platform은 필수입니다.", "code": 400}"""),
                                    @ExampleObject(name = "platform 값 오류", value = """
                                            {"success": false, "message": "잘못된 요청입니다.", "code": 400}""")
                            })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "인증이 필요합니다.", "code": 401}"""))),
            @ApiResponse(responseCode = "404", description = "탈퇴했거나 없는 사용자",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "사용자를 찾을 수 없습니다.", "code": 404}""")))
    })
    @PostMapping
    public ResponseEntity<DeviceResponse> register(
            @LoginUserId Long userId,
            @Valid @RequestBody DeviceRegisterRequest request
    ) {
        DeviceRegisterResult result = userDeviceService.register(userId, request.pushToken(), request.platform());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.device());
    }
}

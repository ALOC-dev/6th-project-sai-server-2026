package com.aloc.sai.auth;

import com.aloc.sai.auth.dto.KakaoLoginRequest;
import com.aloc.sai.auth.dto.KakaoLoginResponse;
import com.aloc.sai.auth.dto.LogoutRequest;
import com.aloc.sai.auth.dto.TokenRefreshRequest;
import com.aloc.sai.auth.dto.TokenRefreshResponse;
import com.aloc.sai.common.exception.ErrorResponse;
import com.aloc.sai.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "카카오 로그인, 토큰 재발급, 로그아웃 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @Operation(summary = "카카오 로그인/회원가입",
            description = "카카오 SDK로 받은 액세스 토큰으로 로그인합니다. 가입되지 않은 카카오 계정이면 자동으로 가입한 뒤 토큰을 발급합니다. "
                    + "신규 가입이면 isNewUser가 true이고, 학번·학과를 등록하기 전까지 profileCompleted는 false입니다. "
                    + "인증 없이 호출합니다.")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "400", description = "kakaoAccessToken 누락",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "kakaoAccessToken은 필수입니다.", "code": 400}"""))),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 카카오 토큰",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "유효하지 않은 카카오 토큰입니다.", "code": 401}"""))),
            @ApiResponse(responseCode = "502", description = "카카오 서버 오류로 사용자 정보를 가져오지 못함",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "카카오 사용자 정보를 가져오지 못했습니다.", "code": 502}""")))
    })
    @PostMapping("/kakao")
    public KakaoLoginResponse loginWithKakao(@Valid @RequestBody KakaoLoginRequest request) {
        return authService.loginWithKakao(request.kakaoAccessToken());
    }

    @Operation(summary = "토큰 재발급",
            description = "리프레시 토큰으로 접근 토큰과 리프레시 토큰을 새로 발급합니다. "
                    + "사용한 리프레시 토큰은 폐기되므로(회전) 응답의 새 리프레시 토큰으로 교체해야 합니다. "
                    + "이미 사용했거나 폐기·만료된 토큰이면 401입니다. 인증 없이 호출합니다.")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재발급 성공"),
            @ApiResponse(responseCode = "400", description = "refreshToken 누락",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "refreshToken은 필수입니다.", "code": 400}"""))),
            @ApiResponse(responseCode = "401", description = "만료되었거나 이미 사용·폐기된 리프레시 토큰",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "만료되었거나 폐기된 리프레시 토큰입니다.", "code": 401}""")))
    })
    @PostMapping("/refresh")
    public TokenRefreshResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @Operation(summary = "로그아웃",
            description = "요청한 리프레시 토큰을 폐기합니다. 본인의 토큰이 아니거나 이미 폐기된 토큰이어도 204를 반환합니다. "
                    + "pushToken을 함께 보내면 그 기기의 푸시 등록도 해제합니다(본인 기기가 아니면 무시).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "로그아웃 성공"),
            @ApiResponse(responseCode = "400", description = "refreshToken 누락",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "refreshToken은 필수입니다.", "code": 400}"""))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "인증이 필요합니다.", "code": 401}""")))
    })
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @LoginUserId Long userId,
            @Valid @RequestBody LogoutRequest request
    ) {
        authService.logout(userId, request.refreshToken(), request.pushToken());
    }
}

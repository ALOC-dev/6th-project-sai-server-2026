package com.aloc.sai.dev;

import com.aloc.sai.common.exception.ErrorResponse;
import com.aloc.sai.security.JwtTokenProvider;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 인증 없이 토큰을 발급하므로 local 프로필에서만 등록한다.
@Tag(name = "Dev", description = "개발용 API (local 프로필 전용)")
@Profile("local")
@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
public class DevTokenController {
    private static final String TEST_USER_NAME = "테스트";

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(summary = "개발용 접근 토큰 발급 (local 전용)",
            description = "카카오 로그인 없이 kakaoId로 접근 토큰을 발급합니다. 해당 kakaoId의 사용자가 없으면 \"테스트\" 이름으로 가입시킵니다. "
                    + "local 프로필에서만 열리며 리프레시 토큰은 발급하지 않습니다. 인증 없이 호출합니다.")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발급 성공"),
            @ApiResponse(responseCode = "400", description = "kakaoId가 없거나 양수가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "kakaoId 누락", value = """
                                            {"success": false, "message": "kakaoId는 필수입니다.", "code": 400}"""),
                                    @ExampleObject(name = "kakaoId가 양수가 아님", value = """
                                            {"success": false, "message": "kakaoId는 양수여야 합니다.", "code": 400}""")
                            }))
    })
    @PostMapping("/token")
    public DevTokenResponse issueToken(@Valid @RequestBody DevTokenRequest request) {
        User user = userService.findOrRegisterKakaoUser(request.kakaoId(), TEST_USER_NAME, null).user();

        return new DevTokenResponse(jwtTokenProvider.createToken(user.getUserId()), user.getUserId(), user.getNickname());
    }

    public record DevTokenRequest(
            @NotNull(message = "kakaoId는 필수입니다.")
            @Positive(message = "kakaoId는 양수여야 합니다.")
            Long kakaoId
    ) {
    }

    public record DevTokenResponse(String accessToken, Long userId, String nickname) {
    }
}

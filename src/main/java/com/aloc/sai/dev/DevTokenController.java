package com.aloc.sai.dev;

import com.aloc.sai.security.JwtTokenProvider;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserService;
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
@Profile("local")
@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
public class DevTokenController {
    private static final String TEST_USER_NAME = "테스트";

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

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

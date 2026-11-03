package com.aloc.sai.auth;

import com.aloc.sai.auth.kakao.KakaoClient;
import com.aloc.sai.auth.kakao.KakaoUserInfo;
import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.security.JwtTokenProvider;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private KakaoClient kakaoClient;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 최초_로그인이면_자동_가입하고_토큰을_발급한다() throws Exception {
        given(kakaoClient.getUserInfo("kakao-token"))
                .willReturn(new KakaoUserInfo(123L, "지윤", "https://k.kakaocdn.net/profile.jpg"));

        login("kakao-token")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.isNewUser").value(true))
                .andExpect(jsonPath("$.profileCompleted").value(false))
                .andExpect(jsonPath("$.user.userId").isNumber())
                .andExpect(jsonPath("$.user.nickname").value(matchesPattern("지윤\\d{4}")))
                .andExpect(jsonPath("$.user.profileImageUrl").value("https://k.kakaocdn.net/profile.jpg"));

        User user = userRepository.findByKakaoId(123L).orElseThrow();
        assertThat(user.getFriendCode()).matches("[A-Z0-9]{8}");
        assertThat(user.isMarketingAgreed()).isFalse();
        assertThat(user.getMarketingAgreedAt()).isNull();
    }

    @Test
    void 기존_회원이면_다시_가입시키지_않고_기기별로_토큰을_저장한다() throws Exception {
        given(kakaoClient.getUserInfo("kakao-token")).willReturn(new KakaoUserInfo(123L, "지윤", null));

        login("kakao-token").andExpect(jsonPath("$.isNewUser").value(true));
        login("kakao-token")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(false))
                .andExpect(jsonPath("$.user.profileImageUrl").value(nullValue()));

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void 리프레시_토큰은_해시로만_저장한다() throws Exception {
        given(kakaoClient.getUserInfo("kakao-token")).willReturn(new KakaoUserInfo(123L, "지윤", null));

        String response = login("kakao-token").andReturn().getResponse().getContentAsString();

        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.getFirst().getTokenHash()).matches("[0-9a-f]{64}");
        assertThat(response).doesNotContain(tokens.getFirst().getTokenHash());
    }

    @Test
    void 발급한_접근_토큰은_인증에_사용할_수_있다() throws Exception {
        given(kakaoClient.getUserInfo("kakao-token")).willReturn(new KakaoUserInfo(123L, "지윤", null));

        String response = login("kakao-token").andReturn().getResponse().getContentAsString();
        String accessToken = response.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");

        Long userId = userRepository.findByKakaoId(123L).orElseThrow().getUserId();
        assertThat(jwtTokenProvider.validateToken(accessToken)).isTrue();
        assertThat(jwtTokenProvider.getTokenType(accessToken)).isEqualTo("access");
        assertThat(jwtTokenProvider.getAuthentication(accessToken).getName()).isEqualTo(String.valueOf(userId));
    }

    @Test
    void 카카오_이름이_없거나_길어도_닉네임을_20자_이내로_만든다() throws Exception {
        given(kakaoClient.getUserInfo("no-name")).willReturn(new KakaoUserInfo(1L, null, null));
        given(kakaoClient.getUserInfo("long-name")).willReturn(new KakaoUserInfo(2L, "가".repeat(30), null));

        login("no-name").andExpect(jsonPath("$.user.nickname").value(matchesPattern("사이\\d{4}")));
        login("long-name").andExpect(jsonPath("$.user.nickname").value(matchesPattern("가{16}\\d{4}")));
    }

    @Test
    void 토큰이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void 잘못된_JSON이면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void 유효하지_않은_카카오_토큰이면_401을_반환한다() throws Exception {
        given(kakaoClient.getUserInfo("invalid"))
                .willThrow(new ApiException(HttpStatus.UNAUTHORIZED, "유효하지 않은 카카오 토큰입니다."));

        login("invalid")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("유효하지 않은 카카오 토큰입니다."))
                .andExpect(jsonPath("$.code").value(401));

        assertThat(userRepository.count()).isZero();
    }

    private ResultActions login(String kakaoAccessToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kakaoAccessToken\":\"" + kakaoAccessToken + "\"}"));
    }
}

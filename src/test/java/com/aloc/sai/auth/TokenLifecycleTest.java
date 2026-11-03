package com.aloc.sai.auth;

import com.aloc.sai.auth.kakao.KakaoClient;
import com.aloc.sai.auth.kakao.KakaoUserInfo;
import com.aloc.sai.security.JwtTokenProvider;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TokenLifecycleTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenCleanupScheduler cleanupScheduler;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @MockitoBean
    private KakaoClient kakaoClient;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        given(kakaoClient.getUserInfo("user-1")).willReturn(new KakaoUserInfo(1L, "지윤", null));
        given(kakaoClient.getUserInfo("user-2")).willReturn(new KakaoUserInfo(2L, "민수", null));
    }

    @Test
    void 재발급하면_새_토큰을_주고_기존_토큰을_폐기한다() throws Exception {
        String oldToken = field(login("user-1"), "refreshToken");

        String response = refresh(oldToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String newToken = field(response, "refreshToken");
        Long userId = userRepository.findByKakaoId(1L).orElseThrow().getUserId();
        assertThat(newToken).isNotEqualTo(oldToken);
        assertThat(jwtTokenProvider.getUserId(newToken)).isEqualTo(userId);
        assertThat(jwtTokenProvider.getUserId(field(response, "accessToken"))).isEqualTo(userId);
        assertThat(refreshTokenRepository.count()).isEqualTo(1);

        refresh(oldToken).andExpect(status().isUnauthorized());
        refresh(newToken).andExpect(status().isOk());
    }

    @Test
    void 기기별_토큰은_서로_영향을_주지_않는다() throws Exception {
        String firstDevice = field(login("user-1"), "refreshToken");
        String secondDevice = field(login("user-1"), "refreshToken");

        refresh(firstDevice).andExpect(status().isOk());

        refresh(secondDevice).andExpect(status().isOk());
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void 잘못된_리프레시_토큰이면_401을_반환한다() throws Exception {
        String loginResponse = login("user-1");

        refresh("invalid-token").andExpect(status().isUnauthorized());
        refresh(field(loginResponse, "accessToken")).andExpect(status().isUnauthorized());

        Long userId = userRepository.findByKakaoId(1L).orElseThrow().getUserId();
        refresh(jwtTokenProvider.createRefreshToken(userId)).andExpect(status().isUnauthorized());

        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    void 만료된_리프레시_토큰이면_DB에_있어도_401을_반환한다() throws Exception {
        login("user-1");
        User user = userRepository.findByKakaoId(1L).orElseThrow();
        String expiredToken = expiredProvider().createRefreshToken(user.getUserId());
        refreshTokenRepository.save(new RefreshToken(user, hash(expiredToken), LocalDateTime.now().minusDays(1)));

        refresh(expiredToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void 리프레시_토큰이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 로그아웃하면_토큰을_폐기하고_재발급할_수_없다() throws Exception {
        String loginResponse = login("user-1");
        String refreshToken = field(loginResponse, "refreshToken");

        logout(field(loginResponse, "accessToken"), refreshToken).andExpect(status().isNoContent());

        assertThat(refreshTokenRepository.count()).isZero();
        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void 로그아웃은_이미_폐기된_토큰이어도_성공한다() throws Exception {
        String loginResponse = login("user-1");
        String accessToken = field(loginResponse, "accessToken");
        String refreshToken = field(loginResponse, "refreshToken");

        logout(accessToken, refreshToken).andExpect(status().isNoContent());
        logout(accessToken, refreshToken).andExpect(status().isNoContent());
    }

    @Test
    void 다른_사용자의_리프레시_토큰은_폐기하지_않는다() throws Exception {
        String victimToken = field(login("user-1"), "refreshToken");
        String attackerAccessToken = field(login("user-2"), "accessToken");

        logout(attackerAccessToken, victimToken).andExpect(status().isNoContent());

        assertThat(refreshTokenRepository.count()).isEqualTo(2);
        refresh(victimToken).andExpect(status().isOk());
    }

    @Test
    void 로그아웃은_유효한_접근_토큰이_없으면_401을_반환한다() throws Exception {
        String loginResponse = login("user-1");
        String refreshToken = field(loginResponse, "refreshToken");
        Long userId = userRepository.findByKakaoId(1L).orElseThrow().getUserId();
        String expiredAccessToken = expiredProvider().createToken(userId);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(refreshToken)))
                .andExpect(status().isUnauthorized());
        logout(field(loginResponse, "accessToken") + "x", refreshToken).andExpect(status().isUnauthorized());
        logout(expiredAccessToken, refreshToken).andExpect(status().isUnauthorized());
        logout(refreshToken, refreshToken).andExpect(status().isUnauthorized());

        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    void 로그아웃은_pushToken이_있어도_성공한다() throws Exception {
        String loginResponse = login("user-1");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + field(loginResponse, "accessToken"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + field(loginResponse, "refreshToken")
                                + "\",\"pushToken\":\"ExponentPushToken[test]\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void 만료된_토큰만_정리한다() throws Exception {
        login("user-1");
        User user = userRepository.findByKakaoId(1L).orElseThrow();
        refreshTokenRepository.save(new RefreshToken(user, "a".repeat(64), LocalDateTime.now().minusMinutes(1)));

        cleanupScheduler.deleteExpiredTokens();

        assertThat(refreshTokenRepository.count()).isEqualTo(1);
        assertThat(refreshTokenRepository.findAll().getFirst().getTokenHash()).isNotEqualTo("a".repeat(64));
    }

    @Test
    void 정리_작업은_매일_새벽_4시에_실행된다() throws Exception {
        Scheduled scheduled = RefreshTokenCleanupScheduler.class
                .getMethod("deleteExpiredTokens")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 0 4 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    private String login(String kakaoAccessToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakaoAccessToken\":\"" + kakaoAccessToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(tokenBody(refreshToken)));
    }

    private ResultActions logout(String accessToken, String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(tokenBody(refreshToken)));
    }

    private String tokenBody(String refreshToken) {
        return "{\"refreshToken\":\"" + refreshToken + "\"}";
    }

    private String field(String json, String name) {
        return json.replaceAll(".*\"" + name + "\":\"([^\"]+)\".*", "$1");
    }

    private JwtTokenProvider expiredProvider() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secretKey", jwtSecret);
        ReflectionTestUtils.setField(provider, "accessExpirationTime", -1000L);
        ReflectionTestUtils.setField(provider, "refreshAccessExpirationTime", -1000L);
        ReflectionTestUtils.invokeMethod(provider, "init");
        return provider;
    }

    private String hash(String token) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}

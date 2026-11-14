package com.aloc.sai.notification;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.auth.kakao.KakaoClient;
import com.aloc.sai.auth.kakao.KakaoUserInfo;
import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.user.UserRepository;
import com.aloc.sai.user.UserService;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserDeviceTest {
    private static final String TOKEN = "ExponentPushToken[abc123]";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private UserDeviceService userDeviceService;

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private KakaoClient kakaoClient;

    @BeforeEach
    void setUp() {
        cleanUp();
        given(kakaoClient.getUserInfo("user-1")).willReturn(new KakaoUserInfo(1L, "지윤", null));
        given(kakaoClient.getUserInfo("user-2")).willReturn(new KakaoUserInfo(2L, "민수", null));
    }

    // 다른 테스트 클래스가 users를 비울 때 FK에 걸리지 않도록 기기를 먼저 지운다.
    @AfterEach
    void cleanUp() {
        userDeviceRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 새_토큰은_201로_등록한다() throws Exception {
        register(login("user-1"), TOKEN, "IOS")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deviceId").isNumber())
                .andExpect(jsonPath("$.platform").value("IOS"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        assertThat(userDeviceRepository.count()).isEqualTo(1);
    }

    @Test
    void 같은_토큰을_다시_등록하면_200으로_갱신한다() throws Exception {
        LoginTokens tokens = login("user-1");
        Long deviceId = deviceId(register(tokens, TOKEN, "IOS").andExpect(status().isCreated()));

        register(tokens, TOKEN, "ANDROID")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value(deviceId))
                .andExpect(jsonPath("$.platform").value("ANDROID"));

        assertThat(userDeviceRepository.count()).isEqualTo(1);
    }

    @Test
    void 같은_기기에서_다른_계정으로_등록하면_토큰이_새_계정으로_옮겨_간다() throws Exception {
        register(login("user-1"), TOKEN, "IOS").andExpect(status().isCreated());

        register(login("user-2"), TOKEN, "IOS").andExpect(status().isOk());

        Long user2Id = userRepository.findByKakaoId(2L).orElseThrow().getUserId();
        assertThat(userDeviceRepository.findByPushToken(TOKEN).orElseThrow().getUser().getUserId()).isEqualTo(user2Id);
        assertThat(userDeviceRepository.count()).isEqualTo(1);
    }

    @Test
    void 같은_토큰이_동시에_등록돼도_하나만_남는다() throws Exception {
        login("user-1");
        Long userId = userRepository.findByKakaoId(1L).orElseThrow().getUserId();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> {
                        start.await();
                        return userDeviceService.register(userId, TOKEN, DevicePlatform.IOS);
                    }),
                    executor.submit(() -> {
                        start.await();
                        return userDeviceService.register(userId, TOKEN, DevicePlatform.IOS);
                    }));
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(userDeviceRepository.count()).isEqualTo(1);
    }

    // 탈퇴 트랜잭션이 사용자 행을 잠근 채 진행 중일 때 등록이 들어오는 상황을 만든다.
    // 등록은 탈퇴가 끝날 때까지 기다렸다가 404로 끝나야 하고, 탈퇴는 FK 위반 없이 성공해야 한다.
    @Test
    void 탈퇴_중에_들어온_등록은_탈퇴가_끝난_뒤_404로_끝난다() throws Exception {
        login("user-1");
        Long userId = userRepository.findByKakaoId(1L).orElseThrow().getUserId();
        CountDownLatch withdrawLocked = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> registration = executor.submit(() -> {
                withdrawLocked.await();
                return userDeviceService.register(userId, TOKEN, DevicePlatform.IOS);
            });

            transactionTemplate.executeWithoutResult(status -> {
                userService.withdraw(userId, "DELETE");
                withdrawLocked.countDown();
                sleep(300);
            });

            assertThatThrownBy(() -> registration.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(ApiException.class)
                    .cause()
                    .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        } finally {
            executor.shutdownNow();
        }

        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(userDeviceRepository.count()).isZero();
    }

    @Test
    void pushToken이_없으면_400을_반환한다() throws Exception {
        registerRaw(login("user-1"), "{\"platform\":\"IOS\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("pushToken은 필수입니다."));
    }

    @Test
    void Expo_토큰_형식이_아니면_400을_반환한다() throws Exception {
        register(login("user-1"), "not-a-token", "IOS")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Expo 푸시 토큰 형식이 아닙니다."));
    }

    @Test
    void platform이_없으면_400을_반환한다() throws Exception {
        registerRaw(login("user-1"), "{\"pushToken\":\"" + TOKEN + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("platform은 필수입니다."));
    }

    @Test
    void platform_값이_잘못되면_400을_반환한다() throws Exception {
        register(login("user-1"), TOKEN, "WINDOWS").andExpect(status().isBadRequest());
        assertThat(userDeviceRepository.count()).isZero();
    }

    @Test
    void 인증_없이_요청하면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pushToken\":\"" + TOKEN + "\",\"platform\":\"IOS\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그아웃할_때_pushToken을_보내면_기기_등록을_해제한다() throws Exception {
        LoginTokens tokens = login("user-1");
        register(tokens, TOKEN, "IOS").andExpect(status().isCreated());

        logout(tokens, "\"pushToken\":\"" + TOKEN + "\"").andExpect(status().isNoContent());

        assertThat(userDeviceRepository.count()).isZero();
    }

    @Test
    void pushToken_없이_로그아웃하면_기기는_남는다() throws Exception {
        LoginTokens tokens = login("user-1");
        register(tokens, TOKEN, "IOS").andExpect(status().isCreated());

        logout(tokens, null).andExpect(status().isNoContent());

        assertThat(userDeviceRepository.count()).isEqualTo(1);
    }

    @Test
    void 다른_사람의_pushToken으로는_등록을_해제하지_않는다() throws Exception {
        register(login("user-1"), TOKEN, "IOS").andExpect(status().isCreated());

        logout(login("user-2"), "\"pushToken\":\"" + TOKEN + "\"").andExpect(status().isNoContent());

        assertThat(userDeviceRepository.count()).isEqualTo(1);
    }

    @Test
    void 기기를_등록한_사용자도_탈퇴할_수_있다() throws Exception {
        LoginTokens tokens = login("user-1");
        register(tokens, TOKEN, "IOS").andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"DELETE\"}"))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByKakaoId(1L)).isEmpty();
        assertThat(userDeviceRepository.count()).isZero();
    }

    private ResultActions register(LoginTokens tokens, String pushToken, String platform) throws Exception {
        return registerRaw(tokens, "{\"pushToken\":\"" + pushToken + "\",\"platform\":\"" + platform + "\"}");
    }

    private ResultActions registerRaw(LoginTokens tokens, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/users/me/devices")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions logout(LoginTokens tokens, String extraField) throws Exception {
        String body = "{\"refreshToken\":\"" + tokens.refreshToken() + "\""
                + (extraField == null ? "" : "," + extraField) + "}";
        return mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Long deviceId(ResultActions result) throws Exception {
        String response = result.andReturn().getResponse().getContentAsString();
        return Long.valueOf(response.replaceAll(".*\"deviceId\":(\\d+).*", "$1"));
    }

    private LoginTokens login(String kakaoAccessToken) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakaoAccessToken\":\"" + kakaoAccessToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new LoginTokens(
                response.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1"),
                response.replaceAll(".*\"refreshToken\":\"([^\"]+)\".*", "$1"));
    }

    // 등록 요청이 잠금을 기다리는 상태가 될 때까지 탈퇴 트랜잭션을 붙잡아 둔다.
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record LoginTokens(String accessToken, String refreshToken) {
    }
}

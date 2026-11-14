package com.aloc.sai.friend;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.auth.kakao.KakaoClient;
import com.aloc.sai.auth.kakao.KakaoUserInfo;
import com.aloc.sai.notification.NotificationRepository;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Locale;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FriendSearchTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private FriendRequestRepository friendRequestRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoBean
    private KakaoClient kakaoClient;

    private String tokenA;
    private String tokenB;
    private User userA;
    private User userB;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        given(kakaoClient.getUserInfo("user-a")).willReturn(new KakaoUserInfo(1L, "가영", null));
        given(kakaoClient.getUserInfo("user-b")).willReturn(new KakaoUserInfo(2L, "나연", null));

        tokenA = login("user-a");
        tokenB = login("user-b");
        userA = userRepository.findByKakaoId(1L).orElseThrow();
        userB = userRepository.findByKakaoId(2L).orElseThrow();
    }

    // 친구·알림 테이블이 users를 참조하므로, 다른 테스트가 사용자를 지울 수 있게 정리하고 끝낸다.
    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        friendRequestRepository.deleteAll();
        friendshipRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 닉네임으로_검색하면_사용자와_관계를_반환한다() throws Exception {
        search(tokenA, "  " + userB.getNickname() + " ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userB.getUserId()))
                .andExpect(jsonPath("$.nickname").value(userB.getNickname()))
                .andExpect(jsonPath("$.friendshipStatus").value("NONE"))
                .andExpect(jsonPath("$.friendRequestId").value(nullValue()));
    }

    @Test
    void 대기_중인_요청이_있으면_방향에_따라_상태와_요청_ID를_반환한다() throws Exception {
        Long requestId = sendRequest(tokenA, userB.getUserId());

        search(tokenA, userB.getNickname())
                .andExpect(jsonPath("$.friendshipStatus").value("REQUEST_SENT"))
                .andExpect(jsonPath("$.friendRequestId").value(requestId));
        search(tokenB, userA.getNickname())
                .andExpect(jsonPath("$.friendshipStatus").value("REQUEST_RECEIVED"))
                .andExpect(jsonPath("$.friendRequestId").value(requestId));
    }

    @Test
    void 친구면_FRIEND이고_거절만_남아_있으면_NONE이다() throws Exception {
        Long rejected = sendRequest(tokenA, userB.getUserId());
        respond(tokenB, rejected, "REJECT");
        search(tokenA, userB.getNickname())
                .andExpect(jsonPath("$.friendshipStatus").value("NONE"))
                .andExpect(jsonPath("$.friendRequestId").value(nullValue()));

        Long accepted = sendRequest(tokenA, userB.getUserId());
        respond(tokenB, accepted, "ACCEPT");
        search(tokenA, userB.getNickname())
                .andExpect(jsonPath("$.friendshipStatus").value("FRIEND"))
                .andExpect(jsonPath("$.friendRequestId").value(nullValue()));

        mockMvc.perform(delete("/api/v1/friends/" + userB.getUserId()).header("Authorization", bearer(tokenA)))
                .andExpect(status().isNoContent());
        search(tokenA, userB.getNickname()).andExpect(jsonPath("$.friendshipStatus").value("NONE"));
    }

    @Test
    void 닉네임이_없거나_비어_있으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/users/search").header("Authorization", bearer(tokenA)))
                .andExpect(status().isBadRequest());
        search(tokenA, "   ").andExpect(status().isBadRequest());
    }

    @Test
    void 본인_닉네임을_검색하면_400을_반환한다() throws Exception {
        search(tokenA, userA.getNickname()).andExpect(status().isBadRequest());
    }

    @Test
    void 일부만_일치하는_닉네임은_404를_반환한다() throws Exception {
        search(tokenA, userB.getNickname().substring(0, 2)).andExpect(status().isNotFound());
    }

    @Test
    void 초대_코드로_조회하면_검색과_같은_응답을_반환한다() throws Exception {
        Long requestId = sendRequest(tokenB, userA.getUserId());

        invite(tokenA, userB.getFriendCode().toLowerCase(Locale.ROOT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userB.getUserId()))
                .andExpect(jsonPath("$.nickname").value(userB.getNickname()))
                .andExpect(jsonPath("$.friendshipStatus").value("REQUEST_RECEIVED"))
                .andExpect(jsonPath("$.friendRequestId").value(requestId));
    }

    @Test
    void 본인_초대_코드면_400_없는_코드면_404를_반환한다() throws Exception {
        invite(tokenA, userA.getFriendCode()).andExpect(status().isBadRequest());
        invite(tokenA, "ZZZZZZZZ").andExpect(status().isNotFound());
    }

    @Test
    void 내_초대_링크는_친구_코드로_만든다() throws Exception {
        mockMvc.perform(get("/api/v1/friend-invites/me").header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(userA.getNickname()))
                .andExpect(jsonPath("$.friendCode").value(userA.getFriendCode()))
                .andExpect(jsonPath("$.inviteUrl").value("https://sai.app/invite/" + userA.getFriendCode()));
    }

    @Test
    void 인증_없이_요청하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/users/search").param("nickname", "가영")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/friend-invites/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/friend-invites/ABCDEFGH")).andExpect(status().isUnauthorized());
    }

    private ResultActions search(String token, String nickname) throws Exception {
        return mockMvc.perform(get("/api/v1/users/search")
                .param("nickname", nickname)
                .header("Authorization", bearer(token)));
    }

    private ResultActions invite(String token, String friendCode) throws Exception {
        return mockMvc.perform(get("/api/v1/friend-invites/" + friendCode).header("Authorization", bearer(token)));
    }

    private Long sendRequest(String token, Long receiverId) throws Exception {
        String json = mockMvc.perform(post("/api/v1/friend-requests")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":" + receiverId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.valueOf(json.replaceAll(".*\"friendRequestId\":(\\d+).*", "$1"));
    }

    private void respond(String token, Long requestId, String action) throws Exception {
        mockMvc.perform(patch("/api/v1/friend-requests/" + requestId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"" + action + "\"}"))
                .andExpect(status().isOk());
    }

    private String login(String kakaoAccessToken) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakaoAccessToken\":\"" + kakaoAccessToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}

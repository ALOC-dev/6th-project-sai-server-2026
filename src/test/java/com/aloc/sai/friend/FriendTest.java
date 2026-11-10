package com.aloc.sai.friend;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.auth.kakao.KakaoClient;
import com.aloc.sai.auth.kakao.KakaoUserInfo;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
class FriendTest {
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
    private FriendService friendService;

    @MockitoBean
    private KakaoClient kakaoClient;

    private String tokenA;
    private String tokenB;
    private String tokenC;
    private Long idA;
    private Long idB;
    private Long idC;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        given(kakaoClient.getUserInfo("user-a")).willReturn(new KakaoUserInfo(1L, "가영", null));
        given(kakaoClient.getUserInfo("user-b")).willReturn(new KakaoUserInfo(2L, "나연", null));
        given(kakaoClient.getUserInfo("user-c")).willReturn(new KakaoUserInfo(3L, "다현", null));

        tokenA = login("user-a");
        tokenB = login("user-b");
        tokenC = login("user-c");
        idA = userId(1L);
        idB = userId(2L);
        idC = userId(3L);
    }

    // 친구 테이블이 users를 참조하므로, 다른 테스트가 사용자를 지울 수 있게 정리하고 끝낸다.
    @AfterEach
    void cleanUp() {
        friendRequestRepository.deleteAll();
        friendshipRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 요청하고_수락하면_양쪽_친구_목록에_보인다() throws Exception {
        Long requestId = sendRequest(tokenA, idB)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiverId").value(idB))
                .andExpect(jsonPath("$.requestStatus").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString()
                .transform(json -> longField(json, "friendRequestId"));

        assertThat(friendService.areFriends(idA, idB)).isFalse();

        respond(tokenB, requestId, "ACCEPT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.friendRequestId").value(requestId))
                .andExpect(jsonPath("$.requestStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.respondedAt").isNotEmpty())
                .andExpect(jsonPath("$.friendshipId").isNumber());

        assertThat(friendService.areFriends(idA, idB)).isTrue();
        assertThat(friendService.areFriends(idB, idA)).isTrue();

        friends(tokenA)
                .andExpect(jsonPath("$.friends.length()").value(1))
                .andExpect(jsonPath("$.friends[0].friendId").value(idB))
                .andExpect(jsonPath("$.friends[0].nickname").isNotEmpty())
                .andExpect(jsonPath("$.friends[0].todayClasses.length()").value(0));
        friends(tokenB)
                .andExpect(jsonPath("$.friends.length()").value(1))
                .andExpect(jsonPath("$.friends[0].friendId").value(idA));
    }

    @Test
    void 친구_목록은_닉네임_가나다순이다() throws Exception {
        befriend(tokenB, idA, tokenA);
        befriend(tokenB, idC, tokenC);

        mockMvc.perform(get("/api/v1/friends").param("available", "true").header("Authorization", bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.friends[0].friendId").value(idA))
                .andExpect(jsonPath("$.friends[1].friendId").value(idC));
    }

    @Test
    void 거절하면_친구가_아니고_다시_요청할_수_있다() throws Exception {
        Long requestId = createRequest(tokenA, idB);

        respond(tokenB, requestId, "REJECT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestStatus").value("REJECTED"))
                .andExpect(jsonPath("$.friendshipId").value(nullValue()));

        assertThat(friendService.areFriends(idA, idB)).isFalse();
        sendRequest(tokenA, idB).andExpect(status().isCreated());
    }

    @Test
    void 받은_요청과_보낸_요청을_나눠서_대기_중인_것만_조회한다() throws Exception {
        Long rejected = createRequest(tokenA, idB);
        respond(tokenB, rejected, "REJECT").andExpect(status().isOk());
        Long fromB = createRequest(tokenB, idA);
        Long toC = createRequest(tokenA, idC);

        mockMvc.perform(get("/api/v1/friend-requests").header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received.length()").value(1))
                .andExpect(jsonPath("$.received[0].friendRequestId").value(fromB))
                .andExpect(jsonPath("$.received[0].user.userId").value(idB))
                .andExpect(jsonPath("$.received[0].user.nickname").isNotEmpty())
                .andExpect(jsonPath("$.received[0].requestStatus").value("PENDING"))
                .andExpect(jsonPath("$.sent.length()").value(1))
                .andExpect(jsonPath("$.sent[0].friendRequestId").value(toC))
                .andExpect(jsonPath("$.sent[0].user.userId").value(idC));

        mockMvc.perform(get("/api/v1/friend-requests").header("Authorization", bearer(tokenC)))
                .andExpect(jsonPath("$.received.length()").value(1))
                .andExpect(jsonPath("$.sent.length()").value(0));
    }

    @Test
    void 자기_자신에게_요청하면_400을_반환한다() throws Exception {
        sendRequest(tokenA, idA).andExpect(status().isBadRequest());
    }

    @Test
    void 없는_사용자에게_요청하면_404를_반환한다() throws Exception {
        sendRequest(tokenA, idC + 1000).andExpect(status().isNotFound());
    }

    @Test
    void 수신자가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/friend-requests")
                        .header("Authorization", bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 대기_중인_요청이_있으면_방향과_관계없이_409를_반환한다() throws Exception {
        createRequest(tokenA, idB);

        sendRequest(tokenA, idB)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 친구 요청을 보냈습니다."));
        sendRequest(tokenB, idA)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("상대가 보낸 친구 요청이 있습니다. 받은 요청에서 수락해 주세요."));

        assertThat(friendRequestRepository.count()).isEqualTo(1);
    }

    @Test
    void 이미_친구면_409를_반환한다() throws Exception {
        befriend(tokenA, idB, tokenB);

        sendRequest(tokenA, idB).andExpect(status().isConflict());
        sendRequest(tokenB, idA).andExpect(status().isConflict());
    }

    @Test
    void 대기_중인_요청은_DB_제약으로도_하나만_허용한다() {
        User a = userRepository.findById(idA).orElseThrow();
        User b = userRepository.findById(idB).orElseThrow();
        friendRequestRepository.saveAndFlush(new FriendRequest(a, b));

        assertThatThrownBy(() -> friendRequestRepository.saveAndFlush(new FriendRequest(b, a)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 받은_사람이_아니면_403을_반환한다() throws Exception {
        Long requestId = createRequest(tokenA, idB);

        respond(tokenA, requestId, "ACCEPT").andExpect(status().isForbidden());
        respond(tokenC, requestId, "ACCEPT").andExpect(status().isForbidden());
        assertThat(friendService.areFriends(idA, idB)).isFalse();
    }

    @Test
    void 이미_처리된_요청이면_409를_반환한다() throws Exception {
        Long requestId = createRequest(tokenA, idB);
        respond(tokenB, requestId, "ACCEPT").andExpect(status().isOk());

        respond(tokenB, requestId, "ACCEPT").andExpect(status().isConflict());
        respond(tokenB, requestId, "REJECT").andExpect(status().isConflict());
        assertThat(friendshipRepository.count()).isEqualTo(1);
    }

    @Test
    void 없는_요청이면_404를_반환한다() throws Exception {
        respond(tokenB, 999_999L, "ACCEPT").andExpect(status().isNotFound());
    }

    @Test
    void action이_잘못되면_400을_반환한다() throws Exception {
        Long requestId = createRequest(tokenA, idB);

        respond(tokenB, requestId, "MAYBE").andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/friend-requests/" + requestId)
                        .header("Authorization", bearer(tokenB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 친구를_삭제하면_양쪽에서_사라지고_다시_요청할_수_있다() throws Exception {
        befriend(tokenA, idB, tokenB);

        mockMvc.perform(delete("/api/v1/friends/" + idA).header("Authorization", bearer(tokenB)))
                .andExpect(status().isNoContent());

        assertThat(friendService.areFriends(idA, idB)).isFalse();
        friends(tokenA).andExpect(jsonPath("$.friends.length()").value(0));
        friends(tokenB).andExpect(jsonPath("$.friends.length()").value(0));
        sendRequest(tokenA, idB).andExpect(status().isCreated());
    }

    @Test
    void 친구가_아닌_사용자를_삭제하면_404를_반환한다() throws Exception {
        mockMvc.perform(delete("/api/v1/friends/" + idB).header("Authorization", bearer(tokenA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 탈퇴하면_친구_관계와_요청이_함께_삭제된다() throws Exception {
        befriend(tokenA, idB, tokenB);
        createRequest(tokenC, idA);

        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"DELETE\"}"))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(idA)).isEmpty();
        assertThat(friendshipRepository.count()).isZero();
        assertThat(friendRequestRepository.count()).isZero();
        friends(tokenB).andExpect(jsonPath("$.friends.length()").value(0));
    }

    @Test
    void 인증_없이_요청하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/friends")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/friend-requests")).andExpect(status().isUnauthorized());
    }

    private void befriend(String requesterToken, Long receiverId, String receiverToken) throws Exception {
        Long requestId = createRequest(requesterToken, receiverId);
        respond(receiverToken, requestId, "ACCEPT").andExpect(status().isOk());
    }

    private Long createRequest(String token, Long receiverId) throws Exception {
        String json = sendRequest(token, receiverId)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return longField(json, "friendRequestId");
    }

    private ResultActions sendRequest(String token, Long receiverId) throws Exception {
        return mockMvc.perform(post("/api/v1/friend-requests")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverId\":" + receiverId + "}"));
    }

    private ResultActions respond(String token, Long requestId, String action) throws Exception {
        return mockMvc.perform(patch("/api/v1/friend-requests/" + requestId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"" + action + "\"}"));
    }

    private ResultActions friends(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/friends").header("Authorization", bearer(token)))
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

    private Long userId(Long kakaoId) {
        return userRepository.findByKakaoId(kakaoId).orElseThrow().getUserId();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private Long longField(String json, String name) {
        return Long.valueOf(json.replaceAll(".*\"" + name + "\":(\\d+).*", "$1"));
    }
}

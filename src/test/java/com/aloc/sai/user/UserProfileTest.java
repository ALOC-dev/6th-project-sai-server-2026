package com.aloc.sai.user;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.auth.kakao.KakaoClient;
import com.aloc.sai.auth.kakao.KakaoUserInfo;
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

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProfileTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private KakaoClient kakaoClient;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        given(kakaoClient.getUserInfo("user-1"))
                .willReturn(new KakaoUserInfo(1L, "지윤", "https://k.kakaocdn.net/profile.png"));
    }

    @Test
    void 내_정보를_조회한다() throws Exception {
        String token = login();
        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"2023920001\",\"department\":\"컴퓨터과학부\","
                                + "\"statusMessage\":\"도서관 3층\",\"marketingAgreed\":true}"))
                .andExpect(status().isOk());
        User user = userRepository.findByKakaoId(1L).orElseThrow();

        getMe(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getUserId()))
                .andExpect(jsonPath("$.nickname").value(user.getNickname()))
                .andExpect(jsonPath("$.profileImageUrl").value("https://k.kakaocdn.net/profile.png"))
                .andExpect(jsonPath("$.statusMessage").value("도서관 3층"))
                .andExpect(jsonPath("$.studentId").value("2023920001"))
                .andExpect(jsonPath("$.department").value("컴퓨터과학부"))
                .andExpect(jsonPath("$.marketingAgreed").value(true))
                .andExpect(jsonPath("$.marketingAgreedAt").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void 초기_설정_전이면_학번과_학과가_null이다() throws Exception {
        getMe(login())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").isEmpty())
                .andExpect(jsonPath("$.department").isEmpty())
                .andExpect(jsonPath("$.marketingAgreed").value(false))
                .andExpect(jsonPath("$.marketingAgreedAt").isEmpty());
    }

    @Test
    void 인증_없이_요청하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakaoAccessToken\":\"user-1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    private ResultActions getMe(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken));
    }
}

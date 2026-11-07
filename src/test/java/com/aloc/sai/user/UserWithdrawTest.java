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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserWithdrawTest {
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
        given(kakaoClient.getUserInfo("user-1")).willReturn(new KakaoUserInfo(1L, "지윤", null));
        given(kakaoClient.getUserInfo("user-2")).willReturn(new KakaoUserInfo(2L, "민수", null));
    }

    @Test
    void 탈퇴하면_사용자와_리프레시_토큰을_삭제한다() throws Exception {
        String loginResponse = login("user-1");
        login("user-1");
        login("user-2");

        withdraw(field(loginResponse, "accessToken"), "DELETE").andExpect(status().isNoContent());

        assertThat(userRepository.findByKakaoId(1L)).isEmpty();
        assertThat(userRepository.findByKakaoId(2L)).isPresent();
        assertThat(refreshTokenRepository.count()).isEqualTo(1);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + field(loginResponse, "refreshToken") + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 탈퇴한_사용자의_접근_토큰으로는_내_정보를_조회할_수_없다() throws Exception {
        String accessToken = field(login("user-1"), "accessToken");

        withdraw(accessToken, "DELETE").andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
        withdraw(accessToken, "DELETE").andExpect(status().isNotFound());
    }

    @Test
    void 탈퇴_후_다시_로그인하면_새로_가입된다() throws Exception {
        withdraw(field(login("user-1"), "accessToken"), "DELETE").andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakaoAccessToken\":\"user-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(true))
                .andExpect(jsonPath("$.profileCompleted").value(false));
    }

    @Test
    void 확인_문구가_틀리거나_없으면_400을_반환하고_삭제하지_않는다() throws Exception {
        String accessToken = field(login("user-1"), "accessToken");

        withdraw(accessToken, "delete").andExpect(status().isBadRequest());
        withdraw(accessToken, "").andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findByKakaoId(1L)).isPresent();
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    void 인증_없이_요청하면_401을_반환한다() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"DELETE\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String login(String kakaoAccessToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakaoAccessToken\":\"" + kakaoAccessToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions withdraw(String accessToken, String confirmation) throws Exception {
        return mockMvc.perform(delete("/api/v1/users/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"" + confirmation + "\"}"));
    }

    private String field(String json, String name) {
        return json.replaceAll(".*\"" + name + "\":\"([^\"]+)\".*", "$1");
    }
}

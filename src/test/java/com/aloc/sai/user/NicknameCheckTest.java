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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NicknameCheckTest {
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
    void 아무도_쓰지_않는_닉네임이면_사용_가능하다() throws Exception {
        check(login("user-1"), "  공강러  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("공강러"))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void 다른_사람이_쓰는_닉네임이면_사용_불가다() throws Exception {
        String token = login("user-1");
        login("user-2");
        String otherNickname = userRepository.findByKakaoId(2L).orElseThrow().getNickname();

        check(token, otherNickname)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void 본인의_현재_닉네임은_사용_가능하다() throws Exception {
        String token = login("user-1");
        String myNickname = userRepository.findByKakaoId(1L).orElseThrow().getNickname();

        check(token, myNickname)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void 닉네임이_없거나_길이가_맞지_않으면_400을_반환한다() throws Exception {
        String token = login("user-1");

        mockMvc.perform(get("/api/v1/users/nickname/check").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        check(token, "   ").andExpect(status().isBadRequest());
        check(token, "가".repeat(21)).andExpect(status().isBadRequest());
    }

    @Test
    void 인증_없이_요청하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/users/nickname/check").param("nickname", "공강러"))
                .andExpect(status().isUnauthorized());
    }

    private String login(String kakaoAccessToken) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakaoAccessToken\":\"" + kakaoAccessToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    private ResultActions check(String accessToken, String nickname) throws Exception {
        return mockMvc.perform(get("/api/v1/users/nickname/check")
                .header("Authorization", "Bearer " + accessToken)
                .param("nickname", nickname));
    }
}

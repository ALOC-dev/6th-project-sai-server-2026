package com.aloc.sai.dev;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.security.JwtTokenProvider;
import com.aloc.sai.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
class DevTokenControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 사용자가_없으면_만들고_접근_토큰을_발급한다() throws Exception {
        String response = issueToken(1L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.nickname").value(matchesPattern("테스트\\d{4}")))
                .andReturn().getResponse().getContentAsString();

        String accessToken = response.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
        Long userId = userRepository.findByKakaoId(1L).orElseThrow().getUserId();
        assertThat(jwtTokenProvider.getTokenType(accessToken)).isEqualTo("access");
        assertThat(jwtTokenProvider.getAuthentication(accessToken).getName()).isEqualTo(String.valueOf(userId));
    }

    @Test
    void 같은_kakaoId면_같은_사용자로_발급하고_다른_kakaoId면_새로_만든다() throws Exception {
        issueToken(1L).andExpect(status().isOk());
        issueToken(1L).andExpect(status().isOk());
        issueToken(2L).andExpect(status().isOk());

        assertThat(userRepository.count()).isEqualTo(2);
    }

    @Test
    void 발급한_토큰은_인증을_통과하고_토큰이_없으면_401을_반환한다() throws Exception {
        String response = issueToken(1L).andReturn().getResponse().getContentAsString();
        String accessToken = response.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/not-found"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/not-found").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void kakaoId가_없거나_양수가_아니면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/dev/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        issueToken(0L).andExpect(status().isBadRequest());
    }

    private ResultActions issueToken(Long kakaoId) throws Exception {
        return mockMvc.perform(post("/api/v1/dev/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kakaoId\":" + kakaoId + "}"));
    }
}

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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserUpdateTest {
    private static final String INITIAL_SETUP = "{\"studentId\":\"2023920001\",\"department\":\"컴퓨터과학부\"}";

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
    void 초기_설정하면_학번과_학과가_등록되고_profileCompleted가_true가_된다() throws Exception {
        String token = login("user-1");

        update(token, "{\"studentId\":\"2023920001\",\"department\":\"컴퓨터과학부\",\"marketingAgreed\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value("2023920001"))
                .andExpect(jsonPath("$.department").value("컴퓨터과학부"))
                .andExpect(jsonPath("$.marketingAgreed").value(true))
                .andExpect(jsonPath("$.marketingAgreedAt").isNotEmpty());

        mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakaoAccessToken\":\"user-1\"}"))
                .andExpect(jsonPath("$.profileCompleted").value(true));
    }

    @Test
    void 초기_설정_전에_학번이나_학과가_빠지면_400을_반환한다() throws Exception {
        String token = login("user-1");

        update(token, "{\"studentId\":\"2023920001\"}").andExpect(status().isBadRequest());
        update(token, "{\"nickname\":\"공강러\"}").andExpect(status().isBadRequest());

        assertThat(userRepository.findByKakaoId(1L).orElseThrow().getStudentId()).isNull();
    }

    @Test
    void 초기_설정_후에는_보낸_필드만_수정한다() throws Exception {
        String token = login("user-1");
        update(token, INITIAL_SETUP).andExpect(status().isOk());

        update(token, "{\"nickname\":\"  공강러  \",\"statusMessage\":\"도서관 3층\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("공강러"))
                .andExpect(jsonPath("$.statusMessage").value("도서관 3층"))
                .andExpect(jsonPath("$.studentId").value("2023920001"));

        update(token, "{\"statusMessage\":\"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusMessage").isEmpty());
    }

    @Test
    void 마케팅_동의_일시는_값이_바뀔_때만_갱신된다() throws Exception {
        String token = login("user-1");
        update(token, "{\"studentId\":\"2023920001\",\"department\":\"컴퓨터과학부\",\"marketingAgreed\":true}");
        LocalDateTime agreedAt = userRepository.findByKakaoId(1L).orElseThrow().getMarketingAgreedAt();

        update(token, "{\"marketingAgreed\":true}").andExpect(status().isOk());
        assertThat(userRepository.findByKakaoId(1L).orElseThrow().getMarketingAgreedAt()).isEqualTo(agreedAt);

        update(token, "{\"marketingAgreed\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketingAgreed").value(false))
                .andExpect(jsonPath("$.marketingAgreedAt").isEmpty());
    }

    @Test
    void 이미_사용_중인_닉네임이면_409를_반환한다() throws Exception {
        String token = login("user-1");
        login("user-2");
        update(token, INITIAL_SETUP);
        String otherNickname = userRepository.findByKakaoId(2L).orElseThrow().getNickname();

        update(token, "{\"nickname\":\"" + otherNickname + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void 자기_닉네임을_그대로_보내면_성공한다() throws Exception {
        String token = login("user-1");
        update(token, INITIAL_SETUP);
        String nickname = userRepository.findByKakaoId(1L).orElseThrow().getNickname();

        update(token, "{\"nickname\":\"" + nickname + "\"}").andExpect(status().isOk());
    }

    @Test
    void 형식이_잘못되면_400을_반환한다() throws Exception {
        String token = login("user-1");
        update(token, INITIAL_SETUP);

        update(token, "{}").andExpect(status().isBadRequest());
        update(token, "{\"nickname\":\"   \"}").andExpect(status().isBadRequest());
        update(token, "{\"nickname\":\"" + "가".repeat(21) + "\"}").andExpect(status().isBadRequest());
        update(token, "{\"studentId\":\"2023-92\"}").andExpect(status().isBadRequest());
        update(token, "{\"department\":\"  \"}").andExpect(status().isBadRequest());
        update(token, "{\"statusMessage\":\"" + "가".repeat(51) + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void 인증_없이_요청하면_401을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INITIAL_SETUP))
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

    private ResultActions update(String accessToken, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/users/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}

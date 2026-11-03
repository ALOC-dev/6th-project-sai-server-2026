package com.aloc.sai.dev;

import com.aloc.sai.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DevTokenDisabledTest {
    private static final long KAKAO_ID = 999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Test
    void local_프로필이_아니면_개발용_빈이_등록되지_않는다() {
        assertThat(context.getBeansOfType(DevTokenController.class)).isEmpty();
        assertThat(context.getBeansOfType(DevSecurityConfig.class)).isEmpty();
    }

    @Test
    void local_프로필이_아니면_토큰을_발급하지_않고_사용자도_만들지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/dev/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakaoId\":" + KAKAO_ID + "}"))
                .andExpect(status().isUnauthorized());

        assertThat(userRepository.findByKakaoId(KAKAO_ID)).isEmpty();
    }
}

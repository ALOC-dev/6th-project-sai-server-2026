package com.aloc.sai.user;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.auth.kakao.KakaoClient;
import com.aloc.sai.auth.kakao.KakaoUserInfo;
import com.aloc.sai.common.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileImageUpdateTest {
    private static final String KAKAO_IMAGE_URL = "https://k.kakaocdn.net/profile.png";
    private static final String S3_IMAGE_URL_1 = "https://bucket.s3.ap-northeast-2.amazonaws.com/profile-images/1/a.png";
    private static final String S3_IMAGE_URL_2 = "https://bucket.s3.ap-northeast-2.amazonaws.com/profile-images/1/b.jpg";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private KakaoClient kakaoClient;

    @Autowired
    private UserService userService;

    @MockitoBean
    private ProfileImageStorage profileImageStorage;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        given(kakaoClient.getUserInfo("user-1")).willReturn(new KakaoUserInfo(1L, "지윤", KAKAO_IMAGE_URL));
    }

    @Test
    void 프로필_이미지를_변경한다() throws Exception {
        String token = login();
        given(profileImageStorage.upload(anyLong(), any(), eq(ProfileImageType.PNG))).willReturn(S3_IMAGE_URL_1);

        updateImage(token, image("profile.png", PNG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").value(S3_IMAGE_URL_1));

        assertThat(userRepository.findByKakaoId(1L).orElseThrow().getProfileImageUrl()).isEqualTo(S3_IMAGE_URL_1);
        verify(profileImageStorage).delete(KAKAO_IMAGE_URL);
    }

    @Test
    void 다시_변경하면_이전_이미지를_삭제한다() throws Exception {
        String token = login();
        given(profileImageStorage.upload(anyLong(), any(), eq(ProfileImageType.PNG))).willReturn(S3_IMAGE_URL_1);
        given(profileImageStorage.upload(anyLong(), any(), eq(ProfileImageType.JPEG))).willReturn(S3_IMAGE_URL_2);

        updateImage(token, image("first.png", PNG)).andExpect(status().isOk());
        updateImage(token, image("second.jpg", JPEG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").value(S3_IMAGE_URL_2));

        verify(profileImageStorage).delete(S3_IMAGE_URL_1);
        verify(profileImageStorage, never()).delete(S3_IMAGE_URL_2);
    }

    @Test
    void 파일명이_아니라_내용으로_형식을_판별한다() throws Exception {
        String token = login();
        given(profileImageStorage.upload(anyLong(), any(), eq(ProfileImageType.JPEG))).willReturn(S3_IMAGE_URL_2);

        updateImage(token, new MockMultipartFile("image", "photo.png", "image/png", JPEG))
                .andExpect(status().isOk());

        verify(profileImageStorage).upload(anyLong(), any(), eq(ProfileImageType.JPEG));
    }

    @Test
    void 이미지가_없으면_400을_반환한다() throws Exception {
        String token = login();

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/users/me/profile-image")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이미지 파일을 첨부해 주세요."));

        verify(profileImageStorage, never()).upload(anyLong(), any(), any());
    }

    @Test
    void 빈_파일이면_400을_반환한다() throws Exception {
        updateImage(login(), image("empty.png", new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이미지 파일을 첨부해 주세요."));
    }

    @Test
    void 지원하지_않는_형식이면_400을_반환한다() throws Exception {
        String token = login();

        updateImage(token, new MockMultipartFile("image", "fake.png", "image/png", "not an image".getBytes()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("JPG, PNG, WEBP, HEIC 이미지만 업로드할 수 있습니다."));

        verify(profileImageStorage, never()).upload(anyLong(), any(), any());
    }

    @Test
    void 용량이_5MB를_넘으면_400을_반환한다() throws Exception {
        byte[] large = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, large, 0, PNG.length);

        updateImage(login(), image("large.png", large))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("프로필 이미지는 5MB 이하만 업로드할 수 있습니다."));

        verify(profileImageStorage, never()).upload(anyLong(), any(), any());
    }

    @Test
    void 저장에_실패하면_500을_반환하고_기존_이미지를_유지한다() throws Exception {
        String token = login();
        given(profileImageStorage.upload(anyLong(), any(), any()))
                .willThrow(new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "프로필 이미지를 저장하지 못했습니다."));

        updateImage(token, image("profile.png", PNG))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("프로필 이미지를 저장하지 못했습니다."));

        assertThat(userRepository.findByKakaoId(1L).orElseThrow().getProfileImageUrl()).isEqualTo(KAKAO_IMAGE_URL);
        verify(profileImageStorage, never()).delete(any());
    }

    @Test
    void 인증_없이_요청하면_401을_반환한다() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/users/me/profile-image").file(image("profile.png", PNG)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 탈퇴하면_올린_프로필_이미지를_삭제한다() throws Exception {
        String token = login();
        given(profileImageStorage.upload(anyLong(), any(), any())).willReturn(S3_IMAGE_URL_1);
        updateImage(token, image("profile.png", PNG)).andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"DELETE\"}"))
                .andExpect(status().isNoContent());

        verify(profileImageStorage).delete(S3_IMAGE_URL_1);
        assertThat(userRepository.findByKakaoId(1L)).isEmpty();
    }

    // 두 요청이 업로드를 마친 뒤 거의 동시에 DB를 바꾸는 상황을 만든다.
    // 잠금이 없으면 둘 다 카카오 URL을 이전 이미지로 읽어, 먼저 반영된 이미지가 S3에 남는다.
    @Test
    void 동시에_변경해도_밀려난_이미지를_삭제한다() throws Exception {
        login();
        Long userId = userRepository.findByKakaoId(1L).orElseThrow().getUserId();
        CyclicBarrier uploaded = new CyclicBarrier(2);
        given(profileImageStorage.upload(anyLong(), any(), any())).willAnswer(invocation -> {
            uploaded.await(5, TimeUnit.SECONDS);
            return Thread.currentThread().getName().endsWith("1") ? S3_IMAGE_URL_1 : S3_IMAGE_URL_2;
        });

        Thread first = new Thread(() -> userService.updateProfileImage(userId, image("a.png", PNG)), "upload-1");
        Thread second = new Thread(() -> userService.updateProfileImage(userId, image("b.png", PNG)), "upload-2");
        first.start();
        second.start();
        first.join(10_000);
        second.join(10_000);

        String finalUrl = userRepository.findById(userId).orElseThrow().getProfileImageUrl();
        String replacedUrl = finalUrl.equals(S3_IMAGE_URL_1) ? S3_IMAGE_URL_2 : S3_IMAGE_URL_1;
        verify(profileImageStorage).delete(KAKAO_IMAGE_URL);
        verify(profileImageStorage).delete(replacedUrl);
        verify(profileImageStorage, never()).delete(finalUrl);
    }

    private MockMultipartFile image(String filename, byte[] content) {
        return new MockMultipartFile("image", filename, "application/octet-stream", content);
    }

    private ResultActions updateImage(String accessToken, MockMultipartFile image) throws Exception {
        return mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/users/me/profile-image")
                .file(image)
                .header("Authorization", "Bearer " + accessToken));
    }

    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakaoAccessToken\":\"user-1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}

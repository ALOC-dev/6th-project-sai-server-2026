package com.aloc.sai.auth.kakao;

import com.aloc.sai.common.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoClientTest {
    private static final String TOKEN_INFO_URI = "https://kapi.kakao.com/v1/user/access_token_info";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";
    private static final long APP_ID = 1635056L;

    private MockRestServiceServer server;
    private KakaoClient kakaoClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        kakaoClient = new KakaoClient(builder, TOKEN_INFO_URI, USER_INFO_URI, APP_ID);
    }

    @Test
    void 카카오_응답을_사용자_정보로_변환한다() {
        expectTokenInfo(APP_ID);
        server.expect(requestTo(USER_INFO_URI))
                .andExpect(header("Authorization", "Bearer kakao-token"))
                .andRespond(withSuccess("""
                        {
                          "id": 123,
                          "connected_at": "2026-11-02T00:00:00Z",
                          "kakao_account": {
                            "profile": {
                              "nickname": "지윤",
                              "profile_image_url": "https://k.kakaocdn.net/profile.jpg",
                              "is_default_image": false
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        KakaoUserInfo userInfo = kakaoClient.getUserInfo("kakao-token");

        assertThat(userInfo).isEqualTo(new KakaoUserInfo(123L, "지윤", "https://k.kakaocdn.net/profile.jpg"));
        server.verify();
    }

    @Test
    void 기본_프로필_이미지도_그대로_저장_대상에_포함한다() {
        expectTokenInfo(APP_ID);
        server.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("""
                        {
                          "id": 123,
                          "kakao_account": {
                            "profile": {
                              "nickname": "지윤",
                              "profile_image_url": "https://k.kakaocdn.net/default.jpg",
                              "is_default_image": true
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(kakaoClient.getUserInfo("kakao-token").profileImageUrl())
                .isEqualTo("https://k.kakaocdn.net/default.jpg");
    }

    @Test
    void 프로필_동의가_없어도_아이디만으로_변환한다() {
        expectTokenInfo(APP_ID);
        server.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("{\"id\": 123}", MediaType.APPLICATION_JSON));

        assertThat(kakaoClient.getUserInfo("kakao-token")).isEqualTo(new KakaoUserInfo(123L, null, null));
    }

    @Test
    void 유효하지_않은_토큰이면_401로_변환한다() {
        server.expect(requestTo(TOKEN_INFO_URI)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> kakaoClient.getUserInfo("invalid"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 카카오_서버_오류는_502로_변환한다() {
        expectTokenInfo(APP_ID);
        server.expect(requestTo(USER_INFO_URI)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> kakaoClient.getUserInfo("kakao-token"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void 다른_카카오_앱에서_발급된_토큰이면_401이고_사용자_정보를_조회하지_않는다() {
        expectTokenInfo(999L);

        assertThatThrownBy(() -> kakaoClient.getUserInfo("other-app-token"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        server.verify();
    }

    @Test
    void 토큰_정보_조회가_서버_오류면_502로_변환한다() {
        server.expect(requestTo(TOKEN_INFO_URI)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> kakaoClient.getUserInfo("kakao-token"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    private void expectTokenInfo(long appId) {
        server.expect(requestTo(TOKEN_INFO_URI))
                .andExpect(header("Authorization", "Bearer " + (appId == APP_ID ? "kakao-token" : "other-app-token")))
                .andRespond(withSuccess("{\"id\": 123, \"expires_in\": 3600, \"app_id\": " + appId + "}",
                        MediaType.APPLICATION_JSON));
    }
}

package com.aloc.sai.auth.kakao;

import com.aloc.sai.common.exception.ApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KakaoClient {
    private final RestClient restClient;
    private final String tokenInfoUri;
    private final String userInfoUri;
    private final long appId;

    public KakaoClient(
            RestClient.Builder restClientBuilder,
            @Value("${kakao.token-info-uri}") String tokenInfoUri,
            @Value("${kakao.user-info-uri}") String userInfoUri,
            @Value("${kakao.app-id}") long appId
    ) {
        this.restClient = restClientBuilder.build();
        this.tokenInfoUri = tokenInfoUri;
        this.userInfoUri = userInfoUri;
        this.appId = appId;
    }

    public KakaoUserInfo getUserInfo(String kakaoAccessToken) {
        try {
            // 다른 카카오 앱에서 발급된 토큰으로 로그인하지 못하도록, 사용자 정보 조회 전에 발급 앱을 확인한다.
            verifyAppId(kakaoAccessToken);

            KakaoUserResponse response = restClient.get()
                    .uri(userInfoUri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .body(KakaoUserResponse.class);

            return toUserInfo(response);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)
                    || e.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)) {
                throw invalidToken(e);
            }
            throw kakaoRequestFailed(e);
        } catch (RestClientException e) {
            throw kakaoRequestFailed(e);
        }
    }

    private void verifyAppId(String kakaoAccessToken) {
        KakaoTokenInfoResponse tokenInfo = restClient.get()
                .uri(tokenInfoUri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                .retrieve()
                .body(KakaoTokenInfoResponse.class);

        if (tokenInfo == null || tokenInfo.appId() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "카카오 사용자 정보를 가져오지 못했습니다.");
        }
        if (tokenInfo.appId() != appId) {
            throw invalidToken(null);
        }
    }

    private ApiException invalidToken(Exception e) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "유효하지 않은 카카오 토큰입니다.", e);
    }

    private ApiException kakaoRequestFailed(Exception e) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "카카오 사용자 정보를 가져오지 못했습니다.", e);
    }

    private KakaoUserInfo toUserInfo(KakaoUserResponse response) {
        if (response == null || response.id() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "카카오 사용자 정보를 가져오지 못했습니다.");
        }

        Profile profile = response.kakaoAccount() == null ? null : response.kakaoAccount().profile();
        if (profile == null) {
            return new KakaoUserInfo(response.id(), null, null);
        }

        return new KakaoUserInfo(response.id(), profile.nickname(), profile.profileImageUrl());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoTokenInfoResponse(@JsonProperty("app_id") Long appId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoUserResponse(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoAccount(Profile profile) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Profile(
            String nickname,
            @JsonProperty("profile_image_url") String profileImageUrl
    ) {
    }
}

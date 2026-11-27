package com.aloc.sai.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

// AI 서버 공통 호출. 주소·내부 키·타임아웃을 한 곳에서 정하고, 모든 실패를 AiException 하나로 바꾼다.
// 엔드포인트별 클라이언트(AiTimetableClient 등)가 이 클래스를 쓰며, 이 패키지는 도메인 타입을 모른다.
@Slf4j
@Component
public class AiClient {
    static final String API_KEY_HEADER = "X-Internal-Api-Key";
    // 가장 오래 걸리는 시간표 인식(로컬 4~5초, 터널 경유 5~10초, AI 서버 제한 9초)에 맞춘다.
    // 엔드포인트별로 달라야 하면 그때 나눈다.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final RestClient restClient;

    @Autowired
    public AiClient(RestClient.Builder restClientBuilder,
                    @Value("${ai.base-url}") String baseUrl,
                    @Value("${ai.internal-api-key}") String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = restClientBuilder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(API_KEY_HEADER, apiKey)
                .build();
    }

    // 테스트에서 MockRestServiceServer를 붙인 RestClient를 넣는다.
    AiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public <T> T post(String path, MediaType contentType, Object body, Class<T> responseType) {
        try {
            return restClient.post()
                    .uri(path)
                    .contentType(contentType)
                    .body(body)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientResponseException e) {
            throw toAiException(path, e);
        } catch (RestClientException e) {
            log.warn("AI 서버 호출 실패: path={}, {}", path, e.getMessage());
            throw new AiException(AiException.UNAVAILABLE_STATUS, null, "AI 서버에 연결하지 못했습니다.", e);
        }
    }

    private static AiException toAiException(String path, RestClientResponseException e) {
        int status = e.getStatusCode().value();
        AiErrorResponse error = readError(e);
        String code = error == null ? null : error.code();
        String message = error == null || error.message() == null ? "AI 서버 오류 응답: " + status : error.message();
        if (status == 401) {
            log.error("AI 서버 내부 키 인증 실패. ai.internal-api-key 설정을 확인하세요. path={}", path);
        } else {
            log.warn("AI 서버 오류 응답: path={}, status={}, code={}", path, status, code);
        }
        return new AiException(status, code, message, e);
    }

    private static AiErrorResponse readError(RestClientResponseException e) {
        try {
            return e.getResponseBodyAs(AiErrorResponse.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    // AI 서버 오류 형식. BE의 ErrorResponse와 달리 code가 문자열이다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiErrorResponse(String code, String message) {
    }
}

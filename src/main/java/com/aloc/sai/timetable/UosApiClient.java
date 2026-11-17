package com.aloc.sai.timetable;

import com.aloc.sai.common.exception.ApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;

// 서울시립대 Open API(wise.uos.ac.kr). 학교 서버가 기본 User-Agent 요청을 막아서 서비스 이름을 보낸다.
@Slf4j
@Component
public class UosApiClient {
    private static final String USER_AGENT = "sai-server/1.0";
    // 한 학기 시간표 응답이 1MB 이상이고 20초 가까이 걸려서 공통 read timeout(5초)보다 길게 잡는다.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
    private static final String FAILED_MESSAGE = "학교 시간표 정보를 가져오지 못했습니다.";

    private final RestClient restClient;
    private final String apiKey;

    public UosApiClient(RestClient.Builder restClientBuilder,
                        @Value("${uos.api-base-url}") String baseUrl,
                        @Value("${uos.api-key}") String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = restClientBuilder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();
        this.apiKey = apiKey;
    }

    public record UosCourse(String subjectNo, String dvclNo, String courseName, String classTime) {
    }

    public List<UosCourse> getCourses(AcademicTerm term) {
        TimeTableResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/COM/ApiTimeTable/list.do")
                            .queryParam("apiKey", "{apiKey}")
                            .queryParam("year", "{year}")
                            .queryParam("term", "{term}")
                            .build(apiKey, term.academicYear(), term.semester().termCode()))
                    .retrieve()
                    .body(TimeTableResponse.class);
        } catch (RestClientException e) {
            // 예외 메시지에 apiKey가 든 요청 URL이 포함될 수 있어 원인 예외를 남기지 않는다.
            log.warn("학교 시간표 API 호출 실패: {}", e.getClass().getSimpleName());
            throw new ApiException(HttpStatus.BAD_GATEWAY, FAILED_MESSAGE);
        }

        // 인증 실패 등은 200 응답에 ERRMSGINFO로 온다.
        if (response == null || response.info() == null) {
            String reason = response == null || response.error() == null ? "응답 없음" : response.error().message();
            log.warn("학교 시간표 API 오류 응답: {}", reason);
            throw new ApiException(HttpStatus.BAD_GATEWAY, FAILED_MESSAGE);
        }
        return response.info().stream()
                .map(row -> new UosCourse(row.subjectNo(), row.dvclNo(), row.subjectName(), row.classTime()))
                .toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TimeTableResponse(@JsonProperty("INFO") List<Row> info,
                                     @JsonProperty("ERRMSGINFO") ErrorInfo error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Row(@JsonProperty("SUBJECT_NO") String subjectNo,
                       @JsonProperty("DVCL_NO") String dvclNo,
                       @JsonProperty("SUBJECT_NM") String subjectName,
                       @JsonProperty("CLASS_NM") String classTime) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorInfo(@JsonProperty("ERRMSG") String message) {
    }
}

package com.aloc.sai.ai;

import com.aloc.sai.ai.dto.AiTimetableRecognitionItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// AiClient 공통 동작(내부 키 헤더, 오류 → AiException 변환)을 시간표 인식 호출로 함께 확인한다.
class AiTimetableClientTest {
    private static final String BASE_URL = "http://ai.test";
    private static final String URL = BASE_URL + AiTimetableClient.RECOGNITION_PATH;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private MockRestServiceServer server;
    private AiTimetableClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(AiClient.API_KEY_HEADER, "internal-key");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AiTimetableClient(new AiClient(builder.build()));
    }

    @Test
    void 이미지를_내부_키와_함께_multipart로_보내고_AI_명세_그대로_받는다() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(AiClient.API_KEY_HEADER, "internal-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andRespond(withSuccess("""
                        {"items": [{"courseName": "자료구조", "dayOfWeek": "MON", "startTime": "09:00", "endTime": "10:15",
                                    "buildingName": "정보기술관", "room": "19-326,27", "confidence": 0.91}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.recognize(PNG, "image/png")).containsExactly(
                new AiTimetableRecognitionItem("자료구조", "MON", "09:00", "10:15", "정보기술관", "19-326,27", 0.91));
        server.verify();
    }

    @Test
    void items가_없으면_빈_목록이다() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.recognize(PNG, "image/png")).isEmpty();
    }

    @Test
    void AI_오류_응답은_상태_코드와_code를_담은_AiException이다() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"INVALID_INPUT\",\"message\":\"이미지가 아닙니다.\"}"));

        assertThatThrownBy(() -> client.recognize(PNG, "image/png"))
                .isInstanceOfSatisfying(AiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(400);
                    assertThat(e.getCode()).isEqualTo("INVALID_INPUT");
                    assertThat(e.getMessage()).isEqualTo("이미지가 아닙니다.");
                });
    }

    @Test
    void 오류_본문이_없어도_상태_코드는_전달한다() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.recognize(PNG, "image/png"))
                .isInstanceOfSatisfying(AiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(401);
                    assertThat(e.getCode()).isNull();
                });
    }

    @Test
    void 연결_실패나_읽기_타임아웃은_503_AiException이다() {
        server.expect(requestTo(URL)).andRespond(withException(new IOException("Read timed out")));

        assertThatThrownBy(() -> client.recognize(PNG, "image/png"))
                .isInstanceOfSatisfying(AiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(AiException.UNAVAILABLE_STATUS));
    }
}

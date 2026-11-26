package com.aloc.sai.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

// Expo Push API(https://docs.expo.dev/push-notifications/sending-notifications/) 호출.
// 지금은 요청 스레드에서 동기로 부르는 단순 버전이라, Expo가 느리거나 죽어도 응답 지연이 이 타임아웃에서 멈추도록 짧게 둔다.
@Component
public class ExpoPushClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;
    private final String pushUri;

    @Autowired
    public ExpoPushClient(RestClient.Builder restClientBuilder, @Value("${expo.push-uri}") String pushUri) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = restClientBuilder.clone().requestFactory(requestFactory).build();
        this.pushUri = pushUri;
    }

    // 테스트에서 MockRestServiceServer를 붙인 RestClient를 넣는다.
    ExpoPushClient(RestClient restClient, String pushUri) {
        this.restClient = restClient;
        this.pushUri = pushUri;
    }

    // 메시지 순서대로 ticket을 돌려준다. HTTP 자체가 실패하면 RestClientException을 그대로 던진다.
    public List<Ticket> send(List<Message> messages) {
        Response response = restClient.post()
                .uri(pushUri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(messages)
                .retrieve()
                .body(Response.class);
        return response == null || response.data() == null ? List.of() : response.data();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(String to, String title, String body, Map<String, Object> data) {
    }

    // status가 "ok"면 id가, "error"면 message와 details.error(예: DeviceNotRegistered)가 온다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ticket(String status, String id, String message, Details details) {
        public boolean isOk() {
            return "ok".equals(status);
        }

        public String error() {
            return details == null ? null : details.error();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Details(String error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Response(List<Ticket> data) {
    }
}

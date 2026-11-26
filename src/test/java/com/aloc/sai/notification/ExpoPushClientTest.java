package com.aloc.sai.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExpoPushClientTest {
    private static final String PUSH_URI = "https://exp.host/--/api/v2/push/send";

    private MockRestServiceServer server;
    private ExpoPushClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ExpoPushClient(builder.build(), PUSH_URI);
    }

    @Test
    void 메시지_배열을_보내고_기기별_ticket을_순서대로_받는다() {
        server.expect(requestTo(PUSH_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        [
                          {"to": "ExponentPushToken[aaa]", "title": "사이", "body": "나연님이 콕 찔렀어요",
                           "data": {"notificationId": 1, "type": "POKE"}},
                          {"to": "ExponentPushToken[bbb]", "title": "사이", "body": "나연님이 콕 찔렀어요"}
                        ]"""))
                .andRespond(withSuccess("""
                        {"data": [
                          {"status": "ok", "id": "ticket-1"},
                          {"status": "error", "message": "not registered",
                           "details": {"error": "DeviceNotRegistered"}}
                        ]}""", MediaType.APPLICATION_JSON));

        List<ExpoPushClient.Ticket> tickets = client.send(List.of(
                new ExpoPushClient.Message("ExponentPushToken[aaa]", "사이", "나연님이 콕 찔렀어요",
                        Map.of("notificationId", 1, "type", "POKE")),
                new ExpoPushClient.Message("ExponentPushToken[bbb]", "사이", "나연님이 콕 찔렀어요", null)));

        assertThat(tickets).hasSize(2);
        assertThat(tickets.get(0).isOk()).isTrue();
        assertThat(tickets.get(0).id()).isEqualTo("ticket-1");
        assertThat(tickets.get(1).isOk()).isFalse();
        assertThat(tickets.get(1).error()).isEqualTo("DeviceNotRegistered");
        server.verify();
    }

    @Test
    void Expo가_5xx면_예외를_던진다() {
        server.expect(requestTo(PUSH_URI)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.send(List.of(
                new ExpoPushClient.Message("ExponentPushToken[aaa]", "사이", "본문", null))))
                .isInstanceOf(RestClientException.class);
    }
}

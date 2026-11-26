package com.aloc.sai.notification;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 알림 커밋 후 받는 사람의 모든 기기로 Expo 푸시를 보낸다(동기 단순 버전)
@SpringBootTest
@ActiveProfiles("test")
class NotificationPushSenderTest {
    private static final String TOKEN_A = "ExponentPushToken[phone-a]";
    private static final String TOKEN_B = "ExponentPushToken[phone-b]";

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserDeviceService userDeviceService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private ExpoPushClient expoPushClient;

    private Long receiver;
    private Long actor;

    @BeforeEach
    void setUp() {
        cleanUp();
        receiver = userRepository.save(new User(1L, "가영", null, "RECEIVER")).getUserId();
        actor = userRepository.save(new User(2L, "나연", null, "ACTOR001")).getUserId();
        given(expoPushClient.send(anyList())).willAnswer(invocation -> {
            List<ExpoPushClient.Message> messages = invocation.getArgument(0);
            return messages.stream().map(m -> new ExpoPushClient.Ticket("ok", "ticket", null, null)).toList();
        });
    }

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        userDeviceRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 받는_사람의_모든_기기로_서버가_만든_문구와_이동_정보를_보낸다() {
        userDeviceService.register(receiver, TOKEN_A, DevicePlatform.IOS);
        userDeviceService.register(receiver, TOKEN_B, DevicePlatform.ANDROID);
        // 보낸 사람 기기로는 가지 않는다.
        userDeviceService.register(actor, "ExponentPushToken[actor]", DevicePlatform.IOS);

        Long notificationId = notificationService.send(receiver, actor, NotificationType.FRIEND_REQUEST, 10L);

        List<ExpoPushClient.Message> messages = sentMessages();
        assertThat(messages).extracting(ExpoPushClient.Message::to).containsExactlyInAnyOrder(TOKEN_A, TOKEN_B);
        assertThat(messages).allSatisfy(m -> {
            assertThat(m.title()).isEqualTo("사이");
            assertThat(m.body()).isEqualTo("나연님이 친구 요청을 보냈어요");
            assertThat(m.data()).containsEntry("notificationId", notificationId)
                    .containsEntry("type", "FRIEND_REQUEST")
                    .containsEntry("referenceId", 10L);
        });
    }

    @Test
    void 타입별_문구가_다르고_보낸_사람이_없으면_이름_없는_문구다() {
        assertThat(NotificationPushSender.body(NotificationType.FRIEND_ACCEPTED, "나연")).isEqualTo("나연님이 친구 요청을 수락했어요");
        assertThat(NotificationPushSender.body(NotificationType.POKE, "나연")).isEqualTo("나연님이 콕 찔렀어요");
        assertThat(NotificationPushSender.body(NotificationType.CHAT_INVITE, "나연")).isEqualTo("나연님이 약속방에 초대했어요");
        assertThat(NotificationPushSender.body(NotificationType.POKE, null)).isEqualTo("새 알림이 있어요");
    }

    @Test
    void 시스템_알림은_보낸_사람_이름_없이_보낸다() {
        userDeviceService.register(receiver, TOKEN_A, DevicePlatform.IOS);

        notificationService.send(receiver, null, NotificationType.POKE, null);

        assertThat(sentMessages()).singleElement()
                .satisfies(m -> assertThat(m.body()).isEqualTo("새 알림이 있어요"));
    }

    @Test
    void 등록된_기기가_없으면_Expo를_부르지_않는다() {
        notificationService.send(receiver, actor, NotificationType.POKE, null);

        verify(expoPushClient, never()).send(any());
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    void Expo_호출이_실패해도_알림은_남고_호출한_쪽은_실패하지_않는다() {
        userDeviceService.register(receiver, TOKEN_A, DevicePlatform.IOS);
        given(expoPushClient.send(anyList())).willThrow(new ResourceAccessException("timeout"));

        notificationService.send(receiver, actor, NotificationType.POKE, null);

        verify(expoPushClient).send(anyList());
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    void 기기별_실패_ticket은_로그만_남기고_토큰은_지우지_않는다() {
        userDeviceService.register(receiver, TOKEN_A, DevicePlatform.IOS);
        given(expoPushClient.send(anyList())).willReturn(List.of(new ExpoPushClient.Ticket(
                "error", null, "not registered", new ExpoPushClient.Details("DeviceNotRegistered"))));

        notificationService.send(receiver, actor, NotificationType.POKE, null);

        assertThat(userDeviceRepository.findByPushToken(TOKEN_A)).isPresent();
    }

    private List<ExpoPushClient.Message> sentMessages() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExpoPushClient.Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(expoPushClient).send(captor.capture());
        return captor.getValue();
    }
}

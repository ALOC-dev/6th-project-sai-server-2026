package com.aloc.sai.notification;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserRepository;
import com.aloc.sai.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceTest {
    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private NotificationPushSender pushSender;

    private Long receiverId;
    private Long actorId;

    @BeforeEach
    void setUp() {
        cleanUp();
        receiverId = userRepository.save(new User(1L, "받는사람", null, "RECEIVER")).getUserId();
        actorId = userRepository.save(new User(2L, "보낸사람", null, "ACTOR001")).getUserId();
    }

    // 다른 테스트 클래스가 users를 비울 때 FK에 걸리지 않도록 알림을 먼저 지운다.
    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 알림을_저장하고_커밋_후_푸시를_보낸다() {
        notificationService.send(receiverId, actorId, NotificationType.FRIEND_REQUEST, 10L);

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        Notification notification = notifications.getFirst();
        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.FRIEND_REQUEST);
        assertThat(notification.getReferenceId()).isEqualTo(10L);
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.getCreatedAt()).isNotNull();

        verify(pushSender).push(new NotificationCreatedEvent(
                notification.getNotificationId(), receiverId, actorId, NotificationType.FRIEND_REQUEST, 10L));
    }

    @Test
    void 찌르기는_referenceId_없이_보낸다() {
        notificationService.send(receiverId, actorId, NotificationType.POKE, null);

        assertThat(notificationRepository.findAll()).singleElement()
                .satisfies(n -> assertThat(n.getReferenceId()).isNull());
    }

    @Test
    void 호출한_트랜잭션이_롤백되면_기록도_푸시도_남지_않는다() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            notificationService.send(receiverId, actorId, NotificationType.CHAT_INVITE, 5L);
            throw new IllegalStateException("호출한 쪽 비즈니스 로직 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(notificationRepository.count()).isZero();
        verify(pushSender, never()).push(any());
    }

    @Test
    void 푸시가_실패해도_호출한_쪽은_실패하지_않는다() {
        doThrow(new IllegalStateException("Expo 장애")).when(pushSender).push(any());

        notificationService.send(receiverId, actorId, NotificationType.FRIEND_ACCEPTED, 10L);

        assertThat(notificationRepository.count()).isEqualTo(1);
        verify(pushSender).push(any());
    }

    @Test
    void referenceId가_필요한_타입에_빠지면_실패한다() {
        assertThatThrownBy(() -> notificationService.send(receiverId, actorId, NotificationType.CHAT_INVITE, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    void 찌르기에_referenceId를_넣으면_실패한다() {
        assertThatThrownBy(() -> notificationService.send(receiverId, actorId, NotificationType.POKE, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 없는_사용자에게는_보낼_수_없다() {
        assertThatThrownBy(() -> notificationService.send(-1L, actorId, NotificationType.POKE, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(pushSender, never()).push(any());
    }

    @Test
    void 알림을_받거나_보낸_사용자도_탈퇴할_수_있다() {
        Long otherId = userRepository.save(new User(3L, "다른사람", null, "OTHER001")).getUserId();
        notificationService.send(receiverId, actorId, NotificationType.POKE, null);
        notificationService.send(actorId, receiverId, NotificationType.POKE, null);
        notificationService.send(otherId, actorId, NotificationType.POKE, null);

        userService.withdraw(receiverId, "DELETE");

        assertThat(userRepository.findById(receiverId)).isEmpty();
        assertThat(notificationRepository.findAll()).singleElement()
                .satisfies(n -> assertThat(n.getReceiver().getUserId()).isEqualTo(otherId));
    }
}

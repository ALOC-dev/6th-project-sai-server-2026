package com.aloc.sai.notification;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 30일이 지난 알림 정리
@SpringBootTest
@ActiveProfiles("test")
class NotificationCleanupTest {
    @Autowired
    private NotificationCleanupScheduler cleanupScheduler;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    private Long receiver;
    private Long actor;

    @BeforeEach
    void setUp() {
        cleanUp();
        receiver = userRepository.save(new User(1L, "받는사람", null, "RECEIVER")).getUserId();
        actor = userRepository.save(new User(2L, "보낸사람", null, "ACTOR001")).getUserId();
    }

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 생성_후_30일이_지난_알림만_읽음_여부와_관계없이_지운다() {
        Long old = createAt(LocalDateTime.now().minusDays(30).minusMinutes(1));
        Long oldRead = createAt(LocalDateTime.now().minusDays(31));
        markRead(oldRead);
        Long recent = createAt(LocalDateTime.now().minusDays(29));
        Long today = createAt(LocalDateTime.now());

        cleanupScheduler.deleteOldNotifications();

        assertThat(notificationRepository.findAll())
                .extracting(Notification::getNotificationId)
                .containsExactlyInAnyOrder(recent, today)
                .doesNotContain(old, oldRead);
    }

    @Test
    void 지울_알림이_없어도_실패하지_않는다() {
        Long recent = createAt(LocalDateTime.now().minusDays(1));

        cleanupScheduler.deleteOldNotifications();

        assertThat(notificationRepository.findAll()).extracting(Notification::getNotificationId).containsExactly(recent);
    }

    @Test
    void 매일_04시_KST에_실행된다() throws Exception {
        Scheduled scheduled = NotificationCleanupScheduler.class
                .getMethod("deleteOldNotifications")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 0 4 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    // createdAt은 @CreationTimestamp라 저장 후 직접 과거로 옮긴다.
    private Long createAt(LocalDateTime createdAt) {
        notificationService.send(receiver, actor, NotificationType.POKE, null);
        Long id = notificationRepository.findAll().stream()
                .map(Notification::getNotificationId).max(Long::compare).orElseThrow();
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createQuery("update Notification n set n.createdAt = :createdAt where n.notificationId = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", id)
                .executeUpdate());
        return id;
    }

    private void markRead(Long notificationId) {
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createQuery("update Notification n set n.isRead = true where n.notificationId = :id")
                .setParameter("id", notificationId)
                .executeUpdate());
    }
}

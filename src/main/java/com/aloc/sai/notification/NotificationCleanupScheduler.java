package com.aloc.sai.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 알림은 생성 후 30일만 보관한다. 알림 창에 오래된 알림을 남기지 않고, 수신자별 알림 수를 약 한 달치로 묶어 둔다.
// 대기 중인 친구 요청은 알림이 지워져도 친구 요청 목록에서 계속 처리할 수 있다.
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {
    static final int RETENTION_DAYS = 30;

    private final NotificationRepository notificationRepository;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteOldNotifications() {
        int deletedCount = notificationRepository.deleteCreatedBefore(LocalDateTime.now().minusDays(RETENTION_DAYS));
        log.info("{}일이 지난 알림 {}개를 삭제했습니다.", RETENTION_DAYS, deletedCount);
    }
}

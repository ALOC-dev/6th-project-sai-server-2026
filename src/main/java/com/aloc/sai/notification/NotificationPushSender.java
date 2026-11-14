package com.aloc.sai.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 알림을 저장한 트랜잭션이 커밋된 뒤에만 푸시를 보낸다. 롤백되면 보내지 않는다.
// 지금은 단순 버전으로 로그만 남긴다. Expo Push 발송은 userDevices와 함께 이 클래스 안에서 붙인다.
@Slf4j
@Component
public class NotificationPushSender {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        // 푸시 실패가 이미 커밋된 비즈니스 로직에 영향을 주지 않도록 여기서 끝낸다.
        try {
            push(event);
        } catch (RuntimeException e) {
            log.warn("푸시 발송 실패: notificationId={}", event.notificationId(), e);
        }
    }

    void push(NotificationCreatedEvent event) {
        log.info("푸시 발송(미구현): notificationId={}, receiverId={}, type={}",
                event.notificationId(), event.receiverId(), event.notificationType());
    }
}

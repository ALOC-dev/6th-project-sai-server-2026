package com.aloc.sai.notification;

import com.aloc.sai.user.UserService;
import com.aloc.sai.user.UserWithdrawEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final UserService userService;
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 각 도메인이 알림을 보낼 때 호출하는 공통 인터페이스. 호출한 쪽은 "언제·누구에게·무슨 알림"만 정한다.
    // 호출한 쪽의 트랜잭션에 참여해 알림 창 기록을 저장하고, 그 트랜잭션이 커밋된 뒤 푸시를 보낸다.
    // 호출한 쪽이 롤백되면 기록도 푸시도 남지 않고, 푸시가 실패해도 호출한 쪽은 실패하지 않는다.
    @Transactional
    public void send(Long receiverId, Long actorId, NotificationType type, Long referenceId) {
        validateReference(type, referenceId);

        Notification notification = notificationRepository.save(new Notification(
                userService.getUser(receiverId),
                userService.getUser(actorId),
                type,
                referenceId));
        eventPublisher.publishEvent(NotificationCreatedEvent.from(notification));
    }

    @EventListener
    @Transactional
    public void deleteAllOnWithdraw(UserWithdrawEvent event) {
        notificationRepository.deleteAllByUserId(event.userId());
    }

    // referenceId를 빠뜨리거나 잘못 넣는 건 호출하는 코드의 실수라 사용자 메시지 없이 실패시킨다.
    private void validateReference(NotificationType type, Long referenceId) {
        if (type.requiresReference() && referenceId == null) {
            throw new IllegalArgumentException(type + " 알림에는 referenceId가 필요합니다.");
        }
        if (!type.requiresReference() && referenceId != null) {
            throw new IllegalArgumentException(type + " 알림에는 referenceId를 넣지 않습니다.");
        }
    }
}

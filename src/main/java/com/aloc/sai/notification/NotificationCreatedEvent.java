package com.aloc.sai.notification;

// 알림이 저장된 트랜잭션이 커밋된 뒤 푸시를 보내기 위한 이벤트. notification 패키지 안에서만 쓴다.
record NotificationCreatedEvent(
        Long notificationId,
        Long receiverId,
        Long actorId,
        NotificationType notificationType,
        Long referenceId
) {
    static NotificationCreatedEvent from(Notification notification) {
        return new NotificationCreatedEvent(
                notification.getNotificationId(),
                notification.getReceiver().getUserId(),
                notification.getActor().getUserId(),
                notification.getNotificationType(),
                notification.getReferenceId()
        );
    }
}

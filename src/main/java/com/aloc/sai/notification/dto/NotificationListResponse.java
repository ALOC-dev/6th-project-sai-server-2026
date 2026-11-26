package com.aloc.sai.notification.dto;

import com.aloc.sai.friend.FriendRequestStatus;
import com.aloc.sai.notification.Notification;
import com.aloc.sai.notification.NotificationType;
import com.aloc.sai.user.User;

import java.time.LocalDateTime;
import java.util.List;

// 알림 문구는 FE가 notificationType과 actor로 만든다. 서버는 문구를 내려주지 않는다.
// nextCursor는 다음 페이지 요청에 그대로 보내는 값이고, 마지막 페이지면 null이다.
public record NotificationListResponse(
        List<NotificationItem> notifications,
        Long nextCursor
) {
    public record NotificationItem(
            Long notificationId,
            NotificationType notificationType,
            Actor actor,
            Long referenceId,
            FriendRequestStatus requestStatus,
            boolean isRead,
            LocalDateTime createdAt
    ) {
        public static NotificationItem from(Notification notification, FriendRequestStatus requestStatus) {
            return new NotificationItem(
                    notification.getNotificationId(),
                    notification.getNotificationType(),
                    Actor.from(notification.getActor()),
                    notification.getReferenceId(),
                    requestStatus,
                    notification.isRead(),
                    notification.getCreatedAt());
        }
    }

    // 시스템 알림처럼 발생시킨 사람이 없으면 actor는 null이다.
    public record Actor(Long userId, String nickname, String profileImageUrl) {
        public static Actor from(User user) {
            if (user == null) {
                return null;
            }
            return new Actor(user.getUserId(), user.getNickname(), user.getProfileImageUrl());
        }
    }
}

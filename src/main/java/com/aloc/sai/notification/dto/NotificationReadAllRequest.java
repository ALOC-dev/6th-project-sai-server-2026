package com.aloc.sai.notification.dto;

import jakarta.validation.constraints.NotNull;

// 알림 창에서 본 가장 최신 알림의 id(목록 응답의 notifications[0].notificationId).
// 이 id 이하만 읽음 처리해, 목록을 받은 뒤 도착한 알림은 안 읽음으로 남긴다.
public record NotificationReadAllRequest(
        @NotNull(message = "lastNotificationId는 필수입니다.")
        Long lastNotificationId
) {
}

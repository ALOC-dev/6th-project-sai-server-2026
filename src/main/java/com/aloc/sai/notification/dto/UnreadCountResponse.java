package com.aloc.sai.notification.dto;

// 홈 화면 알림 배지용. 점으로 그리면 unreadCount > 0, 숫자로 그리면 값 그대로 쓴다.
public record UnreadCountResponse(long unreadCount) {
}

package com.aloc.sai.notification.dto;

import java.time.LocalDateTime;

public record PokeResponse(
        Long notificationId,
        Long targetFriendId,
        LocalDateTime sentAt
) {
}

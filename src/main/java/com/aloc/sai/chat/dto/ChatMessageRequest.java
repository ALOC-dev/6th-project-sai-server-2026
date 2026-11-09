package com.aloc.sai.chat.dto;

public record ChatMessageRequest(
        String type,
        String message,
        Long recommendationRouteId
) {
}

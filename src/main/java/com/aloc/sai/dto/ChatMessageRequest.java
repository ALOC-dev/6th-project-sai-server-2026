package com.aloc.sai.dto;

public record ChatMessageRequest(
        String type,
        String message,
        Long recommendationRouteId
) {
}

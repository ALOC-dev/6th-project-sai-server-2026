package com.aloc.sai.chat.dto;

public record ChatMessageRequest(
        String type, //"Text"/"RECOMMEND_ROUTE"
        String message,
        Long recommendationRouteId
) {
}

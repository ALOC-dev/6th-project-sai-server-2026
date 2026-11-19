package com.aloc.sai.chat.dto;

import java.util.List;

public record ChatMessageListResponse(
        List<ChatMessageResponse> messages,   // 최신 → 과거 순
        Long nextCursor,
        boolean hasNext
) {
}

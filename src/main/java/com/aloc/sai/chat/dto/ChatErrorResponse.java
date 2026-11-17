package com.aloc.sai.chat.dto;

public record ChatErrorResponse(
        boolean success,
        String message,
        int code,
        Long chatRoomId
) {
    public static ChatErrorResponse of(int status, String message, Long chatRoomId){
        return new ChatErrorResponse(
                false,
                message,
                status,
                chatRoomId
        );
    }
}

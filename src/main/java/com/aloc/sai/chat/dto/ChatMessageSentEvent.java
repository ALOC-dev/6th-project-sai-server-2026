package com.aloc.sai.chat.dto;

//메시지 저장 트랜잭션 커밋 후 방에 뿌리기 위한 이벤트
//메시지 저장이 끝남을 알리는 쪽지
public record ChatMessageSentEvent(Long chatMessageId) {
}

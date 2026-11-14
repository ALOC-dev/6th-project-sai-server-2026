package com.aloc.sai.notification;

// referenceId가 가리키는 대상은 타입마다 다르다. 여러 테이블을 가리키므로 FK를 걸지 않는다.
public enum NotificationType {
    // receiver = 친구 요청을 받은 사용자, actor = 보낸 사용자, referenceId = friendRequestId
    FRIEND_REQUEST(true),
    // receiver = 친구 요청을 보낸 사용자, actor = 수락한 사용자, referenceId = friendRequestId
    FRIEND_ACCEPTED(true),
    // referenceId 없음. 알림 창에 기록만 남고 수락·거절 같은 액션이 없다.
    POKE(false),
    // referenceId = chatRoomId
    CHAT_INVITE(true);

    private final boolean requiresReference;

    NotificationType(boolean requiresReference) {
        this.requiresReference = requiresReference;
    }

    public boolean requiresReference() {
        return requiresReference;
    }
}

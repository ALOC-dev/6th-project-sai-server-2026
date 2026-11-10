package com.aloc.sai.friend.dto;

// 검색·초대 링크로 찾은 사용자와 나의 관계. 거절된 요청만 남아 있으면 다시 요청할 수 있으므로 NONE이다.
public enum FriendshipStatus {
    NONE,
    REQUEST_SENT,
    REQUEST_RECEIVED,
    FRIEND
}

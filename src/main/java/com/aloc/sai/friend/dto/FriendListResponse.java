package com.aloc.sai.friend.dto;

import com.aloc.sai.user.User;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;
import java.util.List;

// 안쪽 record 이름은 API 문서의 스키마 이름이 되므로 다른 DTO와 겹치지 않게 짓는다.
public record FriendListResponse(List<FriendItem> friends) {

    public record FriendItem(
            Long friendId,
            Long friendshipId,
            String nickname,
            List<ClassTime> todayClasses
    ) {
        public static FriendItem of(User friend, Long friendshipId, List<ClassTime> todayClasses) {
            return new FriendItem(friend.getUserId(), friendshipId, friend.getNickname(), todayClasses);
        }
    }

    public record ClassTime(
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime
    ) {
    }
}

package com.aloc.sai.friend.dto;

import com.aloc.sai.user.User;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;
import java.util.List;

public record FriendListResponse(List<Item> friends) {

    public record Item(
            Long friendId,
            Long friendshipId,
            String nickname,
            List<ClassTime> todayClasses
    ) {
        public static Item of(User friend, Long friendshipId, List<ClassTime> todayClasses) {
            return new Item(friend.getUserId(), friendshipId, friend.getNickname(), todayClasses);
        }
    }

    public record ClassTime(
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime
    ) {
    }
}

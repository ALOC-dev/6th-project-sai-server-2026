package com.aloc.sai.friend.dto;

import com.aloc.sai.user.User;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;
import java.util.List;

public record FriendListResponse(
        @Schema(description = "친구 목록, 닉네임 가나다순")
        List<Item> friends
) {

    @Schema(name = "FriendItem")
    public record Item(
            @Schema(description = "친구 유저 ID", example = "2")
            Long friendId,

            @Schema(description = "친구관계 아이디", example = "5")
            Long friendshipId,

            @Schema(description = "닉네임", example = "민수")
            String nickname,

            @Schema(description = "친구의 오늘 수업 목록 (현재 학기, 오늘 요일, 시작 시각 오름차순. 수업이 없으면 빈 배열)")
            List<ClassTime> todayClasses
    ) {
        public static Item of(User friend, Long friendshipId, List<ClassTime> todayClasses) {
            return new Item(friend.getUserId(), friendshipId, friend.getNickname(), todayClasses);
        }
    }

    @Schema(name = "FriendClassTime")
    public record ClassTime(
            @Schema(description = "수업 시작 시각", type = "string", example = "10:30")
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,

            @Schema(description = "수업 종료 시각", type = "string", example = "12:00")
            @JsonFormat(pattern = "HH:mm") LocalTime endTime
    ) {
    }
}

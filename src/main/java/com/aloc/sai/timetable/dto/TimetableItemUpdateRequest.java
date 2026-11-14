package com.aloc.sai.timetable.dto;

import com.aloc.sai.timetable.Weekday;

import java.time.LocalTime;

// 보낸 필드만 수정한다. room을 빈 문자열로 보내면 지운다.
public record TimetableItemUpdateRequest(
        String courseName,
        Weekday dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Long buildingId,
        String room
) {
    public boolean isEmpty() {
        return courseName == null && dayOfWeek == null && startTime == null && endTime == null
                && buildingId == null && room == null;
    }
}

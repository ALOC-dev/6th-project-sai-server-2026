package com.aloc.sai.timetable.dto;

import com.aloc.sai.timetable.Semester;
import com.aloc.sai.timetable.Weekday;

import java.time.LocalTime;

// courseId만 보내면 과목 선택 등록, courseId 없이 나머지를 보내면 직접 입력 등록이다.
public record TimetableItemCreateRequest(
        Long courseId,
        Integer academicYear,
        Semester semester,
        String courseName,
        Weekday dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Long buildingId,
        String room
) {
    public boolean hasManualFields() {
        return academicYear != null || semester != null || courseName != null || dayOfWeek != null
                || startTime != null || endTime != null || buildingId != null || room != null;
    }
}

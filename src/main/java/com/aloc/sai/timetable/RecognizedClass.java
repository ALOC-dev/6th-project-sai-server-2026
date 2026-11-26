package com.aloc.sai.timetable;

import com.aloc.sai.course.Weekday;

import java.time.LocalTime;

// 시간표 이미지에서 읽어 낸 수업 하나. 학교 데이터와 맞추기 전의 값이다.
// 인식기가 읽지 못한 값은 null일 수 있고, 매칭 단계에서 필수값이 빠진 수업은 버린다.
public record RecognizedClass(
        String courseName,
        Weekday dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        String buildingName,
        String room,
        Double confidence
) {
}

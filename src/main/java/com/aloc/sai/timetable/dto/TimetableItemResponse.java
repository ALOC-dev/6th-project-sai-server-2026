package com.aloc.sai.timetable.dto;

import com.aloc.sai.timetable.Course;
import com.aloc.sai.timetable.TimetableItem;
import com.aloc.sai.timetable.Weekday;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

// 내 시간표·친구 시간표 조회의 items, 수업 등록의 createdItems에 쓰는 항목 구조
public record TimetableItemResponse(
        Long timetableItemId,
        Long courseId,
        String subjectNo,
        String dvclNo,
        String courseName,
        Weekday dayOfWeek,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        BuildingSummary building,
        String room
) {
    public static TimetableItemResponse from(TimetableItem item) {
        Course course = item.getCourse();
        return new TimetableItemResponse(
                item.getTimetableItemId(),
                course == null ? null : course.getCourseId(),
                course == null ? null : course.getSubjectNo(),
                course == null ? null : course.getDvclNo(),
                item.getCourseName(),
                item.getDayOfWeek(),
                item.getStartTime(),
                item.getEndTime(),
                BuildingSummary.from(item.getBuilding()),
                item.getRoom());
    }
}

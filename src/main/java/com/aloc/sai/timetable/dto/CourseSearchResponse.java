package com.aloc.sai.timetable.dto;

import com.aloc.sai.timetable.Course;
import com.aloc.sai.timetable.CourseSchedule;
import com.aloc.sai.timetable.Weekday;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;
import java.util.List;

// 안쪽 record 이름은 API 문서의 스키마 이름이 되므로 다른 DTO와 겹치지 않게 짓는다.
public record CourseSearchResponse(List<CourseItem> courses) {

    public record CourseItem(
            Long courseId,
            String subjectNo,
            String dvclNo,
            String courseName,
            List<CourseScheduleItem> schedules
    ) {
        public static CourseItem of(Course course, List<CourseSchedule> schedules) {
            return new CourseItem(course.getCourseId(), course.getSubjectNo(), course.getDvclNo(), course.getCourseName(),
                    schedules.stream().map(CourseScheduleItem::from).toList());
        }
    }

    public record CourseScheduleItem(
            Weekday dayOfWeek,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            BuildingSummary building,
            String room
    ) {
        public static CourseScheduleItem from(CourseSchedule schedule) {
            return new CourseScheduleItem(schedule.getDayOfWeek(), schedule.getStartTime(), schedule.getEndTime(),
                    BuildingSummary.from(schedule.getBuilding()), schedule.getRoom());
        }
    }
}

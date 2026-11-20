package com.aloc.sai.timetable.dto;

import com.aloc.sai.timetable.Semester;
import com.aloc.sai.timetable.Weekday;

import java.time.LocalTime;
import java.util.List;

// 이미지 인식 결과를 사용자가 고친 뒤 한 번에 저장한다. 수업이 하나도 없는 시간표에만 쓸 수 있다.
public record TimetableItemBulkCreateRequest(
        Integer academicYear,
        Semester semester,
        List<BulkItem> items
) {
    // 안쪽 record 이름은 API 문서의 스키마 이름이 되므로 다른 DTO와 겹치지 않게 짓는다.
    // courseId는 이미지 인식에서 매칭된 과목이다. 사용자가 시간·강의실을 고쳐도 그대로 보낸다.
    public record BulkItem(
            Long courseId,
            String courseName,
            Weekday dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            Long buildingId,
            String room
    ) {
    }
}

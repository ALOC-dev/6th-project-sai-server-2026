package com.aloc.sai.timetable.dto;

import com.aloc.sai.timetable.AcademicTerm;
import com.aloc.sai.timetable.Semester;
import com.aloc.sai.timetable.Timetable;

import java.time.LocalDateTime;
import java.util.List;

// 시간표가 아직 없으면 timetableId·updatedAt은 null, items는 빈 배열이다.
public record TimetableResponse(
        Long timetableId,
        Integer academicYear,
        Semester semester,
        LocalDateTime updatedAt,
        List<TimetableItemResponse> items
) {
    public static TimetableResponse of(Timetable timetable, List<TimetableItemResponse> items) {
        return new TimetableResponse(timetable.getTimetableId(), timetable.getAcademicYear(), timetable.getSemester(),
                timetable.getUpdatedAt(), items);
    }

    public static TimetableResponse empty(AcademicTerm term) {
        return new TimetableResponse(null, term.academicYear(), term.semester(), null, List.of());
    }
}

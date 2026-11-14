package com.aloc.sai.timetable;

import java.time.LocalDate;
import java.time.ZoneId;

// 연도 + 학기. 날짜·요일 계산은 KST 기준이다.
// 3~8월은 그해 1학기, 9~12월은 그해 2학기, 1~2월은 전년도 2학기로 본다(방학 중에는 직전 학기 시간표를 보여 준다).
public record AcademicTerm(int academicYear, Semester semester) {
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static AcademicTerm current() {
        return of(LocalDate.now(KST));
    }

    public static AcademicTerm of(LocalDate date) {
        int month = date.getMonthValue();
        if (month <= 2) {
            return new AcademicTerm(date.getYear() - 1, Semester.SECOND);
        }
        return new AcademicTerm(date.getYear(), month <= 8 ? Semester.FIRST : Semester.SECOND);
    }
}

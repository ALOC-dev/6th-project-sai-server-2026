package com.aloc.sai.timetable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;

// 연도 + 학기. 날짜·요일 계산은 KST 기준이다.
// 1학기는 3월 첫 평일, 2학기는 9월 첫 평일에 개강해 개강일부터 16주다. 계절학기는 종강 바로 다음 날부터 4주다.
// 방학 중(계절학기 기간 제외)에는 직전 정규 학기 시간표를 보여 준다.
// 겨울 계절학기는 다음 해 1월에 끝나지만 시작한 해의 학기로 본다(학교 API와 같다).
public record AcademicTerm(int academicYear, Semester semester) {
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int SEMESTER_WEEKS = 16;
    private static final int SEASON_WEEKS = 4;

    public static AcademicTerm current() {
        return of(LocalDate.now(KST));
    }

    // 다음 학기. 1학기 → 여름 → 2학기 → 겨울 → 다음 해 1학기
    public AcademicTerm next() {
        return switch (semester) {
            case FIRST -> new AcademicTerm(academicYear, Semester.SUMMER);
            case SUMMER -> new AcademicTerm(academicYear, Semester.SECOND);
            case SECOND -> new AcademicTerm(academicYear, Semester.WINTER);
            case WINTER -> new AcademicTerm(academicYear + 1, Semester.FIRST);
        };
    }

    // 이 학기의 시작일. 정규 학기는 개강일, 계절학기는 직전 정규 학기 종강 다음 날이다.
    public LocalDate startDate() {
        return switch (semester) {
            case FIRST -> openingDay(academicYear, Month.MARCH);
            case SUMMER -> seasonStart(openingDay(academicYear, Month.MARCH));
            case SECOND -> openingDay(academicYear, Month.SEPTEMBER);
            case WINTER -> seasonStart(openingDay(academicYear, Month.SEPTEMBER));
        };
    }

    public static AcademicTerm of(LocalDate date) {
        int year = date.getYear();
        LocalDate springStart = openingDay(year, Month.MARCH);
        LocalDate fallStart = openingDay(year, Month.SEPTEMBER);
        LocalDate summerStart = seasonStart(springStart);
        LocalDate winterStart = seasonStart(fallStart);

        if (date.isBefore(seasonEnd(seasonStart(openingDay(year - 1, Month.SEPTEMBER))))) {
            return new AcademicTerm(year - 1, Semester.WINTER);
        }
        if (date.isBefore(springStart)) {
            return new AcademicTerm(year - 1, Semester.SECOND);
        }
        if (date.isBefore(summerStart)) {
            return new AcademicTerm(year, Semester.FIRST);
        }
        if (date.isBefore(seasonEnd(summerStart))) {
            return new AcademicTerm(year, Semester.SUMMER);
        }
        if (date.isBefore(fallStart)) {
            return new AcademicTerm(year, Semester.FIRST);
        }
        if (date.isBefore(winterStart)) {
            return new AcademicTerm(year, Semester.SECOND);
        }
        return new AcademicTerm(year, Semester.WINTER);
    }

    // 개강일: 그 달의 첫 평일
    static LocalDate openingDay(int year, Month month) {
        LocalDate day = LocalDate.of(year, month, 1);
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.plusDays(1);
        }
        return day;
    }

    // 계절학기 시작일: 개강일부터 16주가 끝난 다음 날
    private static LocalDate seasonStart(LocalDate openingDay) {
        return openingDay.plusWeeks(SEMESTER_WEEKS);
    }

    // 계절학기가 끝난 다음 날(이 날부터는 방학)
    private static LocalDate seasonEnd(LocalDate seasonStart) {
        return seasonStart.plusWeeks(SEASON_WEEKS);
    }
}

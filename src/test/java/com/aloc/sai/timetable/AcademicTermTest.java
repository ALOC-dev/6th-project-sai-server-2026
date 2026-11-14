package com.aloc.sai.timetable;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AcademicTermTest {

    @Test
    void 삼월부터_팔월까지는_그해_1학기다() {
        assertThat(AcademicTerm.of(LocalDate.of(2026, 3, 1))).isEqualTo(new AcademicTerm(2026, Semester.FIRST));
        assertThat(AcademicTerm.of(LocalDate.of(2026, 8, 31))).isEqualTo(new AcademicTerm(2026, Semester.FIRST));
    }

    @Test
    void 구월부터_십이월까지는_그해_2학기다() {
        assertThat(AcademicTerm.of(LocalDate.of(2026, 9, 1))).isEqualTo(new AcademicTerm(2026, Semester.SECOND));
        assertThat(AcademicTerm.of(LocalDate.of(2026, 12, 31))).isEqualTo(new AcademicTerm(2026, Semester.SECOND));
    }

    @Test
    void 일월과_이월은_전년도_2학기다() {
        assertThat(AcademicTerm.of(LocalDate.of(2027, 1, 1))).isEqualTo(new AcademicTerm(2026, Semester.SECOND));
        assertThat(AcademicTerm.of(LocalDate.of(2027, 2, 28))).isEqualTo(new AcademicTerm(2026, Semester.SECOND));
    }

    @Test
    void 요일을_월요일부터_일요일까지_바꾼다() {
        assertThat(Weekday.from(DayOfWeek.MONDAY)).isEqualTo(Weekday.MON);
        assertThat(Weekday.from(DayOfWeek.SUNDAY)).isEqualTo(Weekday.SUN);
    }
}

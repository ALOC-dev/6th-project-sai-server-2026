package com.aloc.sai.timetable;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;

import static org.assertj.core.api.Assertions.assertThat;

class AcademicTermTest {

    @Test
    void 개강일은_그_달의_첫_평일이다() {
        assertThat(AcademicTerm.openingDay(2026, Month.MARCH)).isEqualTo(LocalDate.of(2026, 3, 2));
        assertThat(AcademicTerm.openingDay(2026, Month.SEPTEMBER)).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void 삼월_개강일부터_16주는_1학기이고_개강_전은_전년도_2학기다() {
        assertThat(AcademicTerm.of(LocalDate.of(2026, 3, 1))).isEqualTo(new AcademicTerm(2025, Semester.SECOND));
        assertThat(AcademicTerm.of(LocalDate.of(2026, 3, 2))).isEqualTo(new AcademicTerm(2026, Semester.FIRST));
        assertThat(AcademicTerm.of(LocalDate.of(2026, 6, 21))).isEqualTo(new AcademicTerm(2026, Semester.FIRST));
    }

    @Test
    void 일학기_종강_다음_날부터_4주는_여름_계절학기다() {
        assertThat(AcademicTerm.of(LocalDate.of(2026, 6, 22))).isEqualTo(new AcademicTerm(2026, Semester.SUMMER));
        assertThat(AcademicTerm.of(LocalDate.of(2026, 7, 19))).isEqualTo(new AcademicTerm(2026, Semester.SUMMER));
    }

    @Test
    void 여름_계절학기가_끝나면_2학기_개강_전까지_1학기다() {
        assertThat(AcademicTerm.of(LocalDate.of(2026, 7, 20))).isEqualTo(new AcademicTerm(2026, Semester.FIRST));
        assertThat(AcademicTerm.of(LocalDate.of(2026, 8, 31))).isEqualTo(new AcademicTerm(2026, Semester.FIRST));
    }

    @Test
    void 구월_개강일부터_16주는_2학기다() {
        assertThat(AcademicTerm.of(LocalDate.of(2026, 9, 1))).isEqualTo(new AcademicTerm(2026, Semester.SECOND));
        assertThat(AcademicTerm.of(LocalDate.of(2026, 12, 21))).isEqualTo(new AcademicTerm(2026, Semester.SECOND));
    }

    @Test
    void 이학기_종강_다음_날부터_4주는_시작한_해의_겨울_계절학기다() {
        assertThat(AcademicTerm.of(LocalDate.of(2026, 12, 22))).isEqualTo(new AcademicTerm(2026, Semester.WINTER));
        assertThat(AcademicTerm.of(LocalDate.of(2027, 1, 18))).isEqualTo(new AcademicTerm(2026, Semester.WINTER));
        assertThat(AcademicTerm.of(LocalDate.of(2026, 1, 18))).isEqualTo(new AcademicTerm(2025, Semester.WINTER));
    }

    @Test
    void 겨울_계절학기가_끝나면_1학기_개강_전까지_2학기다() {
        assertThat(AcademicTerm.of(LocalDate.of(2027, 1, 19))).isEqualTo(new AcademicTerm(2026, Semester.SECOND));
        assertThat(AcademicTerm.of(LocalDate.of(2026, 1, 19))).isEqualTo(new AcademicTerm(2025, Semester.SECOND));
    }

    @Test
    void 학기_시작일은_개강일이고_계절학기는_종강_다음_날이다() {
        assertThat(new AcademicTerm(2026, Semester.FIRST).startDate()).isEqualTo(LocalDate.of(2026, 3, 2));
        assertThat(new AcademicTerm(2026, Semester.SUMMER).startDate()).isEqualTo(LocalDate.of(2026, 6, 22));
        assertThat(new AcademicTerm(2026, Semester.SECOND).startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(new AcademicTerm(2026, Semester.WINTER).startDate()).isEqualTo(LocalDate.of(2026, 12, 22));
    }

    @Test
    void 다음_학기는_1학기_여름_2학기_겨울_순이다() {
        assertThat(new AcademicTerm(2026, Semester.FIRST).next()).isEqualTo(new AcademicTerm(2026, Semester.SUMMER));
        assertThat(new AcademicTerm(2026, Semester.SUMMER).next()).isEqualTo(new AcademicTerm(2026, Semester.SECOND));
        assertThat(new AcademicTerm(2026, Semester.SECOND).next()).isEqualTo(new AcademicTerm(2026, Semester.WINTER));
        assertThat(new AcademicTerm(2026, Semester.WINTER).next()).isEqualTo(new AcademicTerm(2027, Semester.FIRST));
    }

    @Test
    void 학교_API_학기_코드는_10_11_20_21이다() {
        assertThat(Semester.FIRST.termCode()).isEqualTo(10);
        assertThat(Semester.SUMMER.termCode()).isEqualTo(11);
        assertThat(Semester.SECOND.termCode()).isEqualTo(20);
        assertThat(Semester.WINTER.termCode()).isEqualTo(21);
    }

    @Test
    void 요일을_월요일부터_일요일까지_바꾼다() {
        assertThat(Weekday.from(DayOfWeek.MONDAY)).isEqualTo(Weekday.MON);
        assertThat(Weekday.from(DayOfWeek.SUNDAY)).isEqualTo(Weekday.SUN);
    }
}

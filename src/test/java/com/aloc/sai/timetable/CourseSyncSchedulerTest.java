package com.aloc.sai.timetable;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CourseSyncSchedulerTest {

    @Test
    void 개강_2주_전부터_동기화한다() {
        // 2027년 1학기 개강일은 3/1(월)
        assertThat(CourseSyncScheduler.termsToSync(LocalDate.of(2027, 2, 15)))
                .containsExactly(new AcademicTerm(2027, Semester.FIRST));
        assertThat(CourseSyncScheduler.termsToSync(LocalDate.of(2027, 2, 14))).isEmpty();
    }

    @Test
    void 개강_후_2주까지_동기화한다() {
        assertThat(CourseSyncScheduler.termsToSync(LocalDate.of(2026, 9, 14)))
                .containsExactly(new AcademicTerm(2026, Semester.SECOND));
        assertThat(CourseSyncScheduler.termsToSync(LocalDate.of(2026, 9, 15))).isEmpty();
    }

    @Test
    void 계절학기도_시작일_앞뒤_2주만_동기화한다() {
        assertThat(CourseSyncScheduler.termsToSync(LocalDate.of(2026, 12, 8)))
                .containsExactly(new AcademicTerm(2026, Semester.WINTER));
        assertThat(CourseSyncScheduler.termsToSync(LocalDate.of(2027, 1, 4)))
                .containsExactly(new AcademicTerm(2026, Semester.WINTER));
        assertThat(CourseSyncScheduler.termsToSync(LocalDate.of(2027, 1, 5))).isEmpty();
    }

    @Test
    void 학기_중간에는_동기화하지_않는다() {
        assertThat(CourseSyncScheduler.termsToSync(LocalDate.of(2026, 11, 17))).isEmpty();
        assertThat(CourseSyncScheduler.termsToSync(LocalDate.of(2026, 4, 20))).isEmpty();
    }
}

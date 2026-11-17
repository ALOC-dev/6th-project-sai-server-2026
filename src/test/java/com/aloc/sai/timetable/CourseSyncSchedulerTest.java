package com.aloc.sai.timetable;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CourseSyncSchedulerTest {

    @Test
    void 개강_4주_전부터_동기화한다() {
        // 2027년 1학기 개강일은 3/1(월)
        assertThat(CourseSyncScheduler.termsToSync(LocalDate.of(2027, 2, 1)))
                .containsExactly(new AcademicTerm(2027, Semester.FIRST));
        assertThat(CourseSyncScheduler.termsToSync(LocalDate.of(2027, 1, 31))).isEmpty();
    }

    @Test
    void 개강_후_2주까지_동기화한다() {
        assertThat(CourseSyncScheduler.termsToSync(LocalDate.of(2026, 9, 14)))
                .containsExactly(new AcademicTerm(2026, Semester.SECOND));
        assertThat(CourseSyncScheduler.termsToSync(LocalDate.of(2026, 9, 15))).isEmpty();
    }

    @Test
    void 계절학기도_시작일_4주_전부터_2주_후까지만_동기화한다() {
        // 2026년 겨울 계절학기 시작일은 12/22
        assertThat(CourseSyncScheduler.termsToSync(LocalDate.of(2026, 11, 24)))
                .containsExactly(new AcademicTerm(2026, Semester.WINTER));
        assertThat(CourseSyncScheduler.termsToSync(LocalDate.of(2026, 11, 23))).isEmpty();
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

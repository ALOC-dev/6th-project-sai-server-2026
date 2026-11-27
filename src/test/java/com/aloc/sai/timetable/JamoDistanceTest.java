package com.aloc.sai.timetable;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JamoDistanceTest {

    @Test
    void 한글은_초성_중성_종성으로_푼다() {
        assertThat(JamoDistance.decompose("머신러닝")).hasSize(10);  // ㅁㅓ ㅅㅣㄴ ㄹㅓ ㄴㅣㅇ
        assertThat(JamoDistance.decompose("근로")).hasSize(5);      // ㄱㅡㄴ ㄹㅗ
        assertThat(JamoDistance.decompose("AI")).hasSize(2);
    }

    @Test
    void 비슷한_모양의_자모를_헷갈린_오타는_거리가_작다() {
        assertThat(distance("공학연구인런십", "공학연구인턴십")).isEqualTo(1);  // ㄹ→ㅌ
        assertThat(distance("머신러널", "머신러닝")).isEqualTo(2);            // ㅓ→ㅣ, ㄹ→ㅇ
        assertThat(distance("자료구조", "자료구조")).isZero();
    }

    @Test
    void 많이_틀리면_거리가_크다() {
        assertThat(distance("스도그대방", "프로그래밍")).isGreaterThan(4);
    }

    private static int distance(String a, String b) {
        return JamoDistance.distance(JamoDistance.decompose(a), JamoDistance.decompose(b));
    }
}

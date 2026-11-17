package com.aloc.sai.timetable;

import com.aloc.sai.timetable.ClassTimeParser.ClassTime;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassTimeParserTest {

    @Test
    void 연속된_교시는_하나의_수업_시간이다() {
        List<ClassTime> result = ClassTimeParser.parse("월[8,9]/33-402");

        assertThat(result).containsExactly(
                new ClassTime(Weekday.MON, LocalTime.of(16, 0), LocalTime.of(17, 50), "33", "402"));
    }

    @Test
    void 여러_요일과_쉼표가_든_호실을_나눈다() {
        List<ClassTime> result = ClassTimeParser.parse("월[1]/20-201,2,수[1,2]/20-201,2,목[6,7]/4-213,4");

        assertThat(result).containsExactly(
                new ClassTime(Weekday.MON, LocalTime.of(9, 0), LocalTime.of(9, 50), "20", "201,2"),
                new ClassTime(Weekday.WED, LocalTime.of(9, 0), LocalTime.of(10, 50), "20", "201,2"),
                new ClassTime(Weekday.THU, LocalTime.of(14, 0), LocalTime.of(15, 50), "4", "213,4"));
    }

    @Test
    void 이어지지_않는_교시는_구간마다_나눈다() {
        List<ClassTime> result = ClassTimeParser.parse("월[2,3,5,6]/15-125,26,27,28");

        assertThat(result).containsExactly(
                new ClassTime(Weekday.MON, LocalTime.of(10, 0), LocalTime.of(11, 50), "15", "125,26,27,28"),
                new ClassTime(Weekday.MON, LocalTime.of(13, 0), LocalTime.of(14, 50), "15", "125,26,27,28"));
    }

    @Test
    void 야간_교시는_학교_교시표를_따른다() {
        List<ClassTime> result = ClassTimeParser.parse("화[10,11,12]/19-B102/103,토[15]/5-301-2");

        assertThat(result).containsExactly(
                new ClassTime(Weekday.TUE, LocalTime.of(18, 0), LocalTime.of(20, 20), "19", "B102/103"),
                new ClassTime(Weekday.SAT, LocalTime.of(21, 55), LocalTime.of(22, 40), "5", "301-2"));
    }

    @Test
    void 주간과_야간_교시가_이어지면_한_구간이다() {
        List<ClassTime> result = ClassTimeParser.parse("금[9,10]/1-105");

        assertThat(result).containsExactly(
                new ClassTime(Weekday.FRI, LocalTime.of(17, 0), LocalTime.of(18, 45), "1", "105"));
    }

    @Test
    void 건물_번호가_없는_장소는_원문을_호실로_둔다() {
        assertThat(ClassTimeParser.parse("목[3]/37-나502")).containsExactly(
                new ClassTime(Weekday.THU, LocalTime.of(11, 0), LocalTime.of(11, 50), "37", "나502"));
        assertThat(ClassTimeParser.parse("목[3]/학외실습")).containsExactly(
                new ClassTime(Weekday.THU, LocalTime.of(11, 0), LocalTime.of(11, 50), null, "학외실습"));
        assertThat(ClassTimeParser.parse("목[3]/")).containsExactly(
                new ClassTime(Weekday.THU, LocalTime.of(11, 0), LocalTime.of(11, 50), null, null));
    }

    @Test
    void 비어_있으면_빈_목록이다() {
        assertThat(ClassTimeParser.parse(null)).isEmpty();
        assertThat(ClassTimeParser.parse("  ")).isEmpty();
    }

    @Test
    void 형식이_맞지_않으면_예외다() {
        assertThatThrownBy(() -> ClassTimeParser.parse("월요일 1교시"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClassTimeParser.parse("월[]/1-101"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClassTimeParser.parse("월[1]1-101"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 범위를_벗어난_교시는_예외다() {
        assertThatThrownBy(() -> ClassTimeParser.parse("월[16]/1-101"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClassTimeParser.parse("월[0,1]/1-101"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.aloc.sai.timetable;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 학교 시간표 API의 강의시간 문자열(CLASS_NM)을 요일별 수업 시간으로 바꾼다.
// 형식: "요일[교시,...]/건물번호-호실"을 쉼표로 이어 붙인다. 예) "월[8,9]/33-402,수[8]/33-402"
// 호실에도 쉼표가 들어갈 수 있어("4-204,5,6") 다음 "요일[" 앞에서 끊는다.
public final class ClassTimeParser {
    private static final Pattern SEGMENT =
            Pattern.compile("([월화수목금토일])\\[([0-9,]+)]/(.*?)(?=,[월화수목금토일]\\[|$)");
    private static final Map<String, Weekday> WEEKDAYS = Map.of(
            "월", Weekday.MON, "화", Weekday.TUE, "수", Weekday.WED, "목", Weekday.THU,
            "금", Weekday.FRI, "토", Weekday.SAT, "일", Weekday.SUN);
    private static final int MAX_DAY_PERIOD = 9;
    private static final int MAX_PERIOD = 15;
    private static final int MAX_ROOM_LENGTH = 50;

    private ClassTimeParser() {
    }

    // buildingNumber는 캠퍼스 건물 번호, room은 건물 번호를 뺀 호실 표기 원문이다. 둘 다 없을 수 있다.
    public record ClassTime(Weekday dayOfWeek, LocalTime startTime, LocalTime endTime,
                            String buildingNumber, String room) {
    }

    // 비어 있으면 빈 목록, 형식이 맞지 않으면 IllegalArgumentException
    public static List<ClassTime> parse(String classTime) {
        if (classTime == null || classTime.isBlank()) {
            return List.of();
        }
        String text = classTime.strip();
        Matcher matcher = SEGMENT.matcher(text);
        List<ClassTime> result = new ArrayList<>();
        int position = 0;
        while (position < text.length()) {
            matcher.region(position, text.length());
            if (!matcher.lookingAt()) {
                throw new IllegalArgumentException("강의시간 형식이 올바르지 않습니다: " + classTime);
            }
            addSegment(result, WEEKDAYS.get(matcher.group(1)), matcher.group(2), matcher.group(3).strip());
            position = matcher.end();
            if (position < text.length()) {
                position++; // 다음 요일 앞의 쉼표
            }
        }
        return result;
    }

    // 교시가 이어지지 않으면("월[2,3,5,6]") 이어진 구간마다 수업 시간을 나눈다.
    private static void addSegment(List<ClassTime> result, Weekday day, String periodText, String location) {
        TreeSet<Integer> periods = new TreeSet<>();
        for (String period : periodText.split(",")) {
            if (period.isEmpty()) {
                continue;
            }
            int value = Integer.parseInt(period);
            if (value < 1 || value > MAX_PERIOD) {
                throw new IllegalArgumentException("교시는 1~15 사이여야 합니다: " + value);
            }
            periods.add(value);
        }
        if (periods.isEmpty()) {
            throw new IllegalArgumentException("교시가 없습니다.");
        }

        String buildingNumber = null;
        String room = location.isEmpty() ? null : location;
        int dash = location.indexOf('-');
        if (dash > 0 && location.substring(0, dash).chars().allMatch(Character::isDigit)) {
            buildingNumber = String.valueOf(Integer.parseInt(location.substring(0, dash)));
            String rest = location.substring(dash + 1).strip();
            room = rest.isEmpty() ? null : rest;
        }
        if (room != null && room.length() > MAX_ROOM_LENGTH) {
            room = room.substring(0, MAX_ROOM_LENGTH);
        }

        Integer runStart = null;
        Integer previous = null;
        for (int period : periods) {
            if (runStart == null) {
                runStart = period;
            } else if (period != previous + 1) {
                result.add(new ClassTime(day, startOf(runStart), endOf(previous), buildingNumber, room));
                runStart = period;
            }
            previous = period;
        }
        result.add(new ClassTime(day, startOf(runStart), endOf(previous), buildingNumber, room));
    }

    // 주간 1~9교시: 09:00부터 1시간 간격, 50분 수업
    // 야간 10~15교시: 45분 수업, 학교 교시표 기준(간격이 일정하지 않아 계산하지 않는다)
    private static final LocalTime[][] NIGHT_PERIODS = {
            {LocalTime.of(18, 0), LocalTime.of(18, 45)},
            {LocalTime.of(18, 45), LocalTime.of(19, 30)},
            {LocalTime.of(19, 35), LocalTime.of(20, 20)},
            {LocalTime.of(20, 20), LocalTime.of(21, 5)},
            {LocalTime.of(21, 10), LocalTime.of(21, 55)},
            {LocalTime.of(21, 55), LocalTime.of(22, 40)},
    };

    static LocalTime startOf(int period) {
        if (period <= MAX_DAY_PERIOD) {
            return LocalTime.of(8 + period, 0);
        }
        return NIGHT_PERIODS[period - 10][0];
    }

    static LocalTime endOf(int period) {
        if (period <= MAX_DAY_PERIOD) {
            return LocalTime.of(8 + period, 50);
        }
        return NIGHT_PERIODS[period - 10][1];
    }
}

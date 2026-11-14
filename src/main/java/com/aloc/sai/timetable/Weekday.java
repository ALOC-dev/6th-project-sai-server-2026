package com.aloc.sai.timetable;

import java.time.DayOfWeek;

// API·DB에서 쓰는 요일 표기(MON~SUN). 선언 순서가 월~일 정렬 순서다.
public enum Weekday {
    MON, TUE, WED, THU, FRI, SAT, SUN;

    public static Weekday from(DayOfWeek dayOfWeek) {
        return values()[dayOfWeek.getValue() - 1];
    }
}

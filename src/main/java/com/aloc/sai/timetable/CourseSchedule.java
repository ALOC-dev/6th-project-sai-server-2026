package com.aloc.sai.timetable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

// 과목 하나의 요일별 수업 시간. 한 과목이 월·수처럼 여러 개를 가질 수 있다.
@Entity
@Table(name = "course_schedules", indexes = @Index(name = "idx_course_schedule_course", columnList = "course_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long courseScheduleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    // 강의실 표기가 건물 데이터와 매칭되지 않으면 null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private Building building;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Weekday dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(length = 50)
    private String room;

    public CourseSchedule(Course course, Weekday dayOfWeek, LocalTime startTime, LocalTime endTime,
                          Building building, String room) {
        this.course = course;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.building = building;
        this.room = room;
    }
}

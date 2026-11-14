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

// 시간표의 수업 한 칸(요일 하나, 시간 하나). 학교 과목에서 만들면 course가 있고, 직접 입력하면 null이다.
@Entity
@Table(name = "timetable_items", indexes = {
        // 요일별 수업 조회와 시간 겹침 검사, 공강 계산용
        @Index(name = "idx_timetable_item_timetable_day", columnList = "timetable_id, dayOfWeek")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimetableItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long timetableItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "timetable_id", nullable = false)
    private Timetable timetable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private Building building;

    // 학교 과목명은 50자를 넘을 수 있어 컬럼은 100자로 둔다. 직접 입력은 50자까지 받는다.
    @Column(nullable = false, length = 100)
    private String courseName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Weekday dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(length = 50)
    private String room;

    public TimetableItem(Timetable timetable, Course course, String courseName, Weekday dayOfWeek,
                         LocalTime startTime, LocalTime endTime, Building building, String room) {
        this.timetable = timetable;
        this.course = course;
        this.courseName = courseName;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.building = building;
        this.room = room;
    }

    // 학교 과목에서 만든 수업도 시간·강의실을 고칠 수 있다. 같은 과목을 듣는다는 사실은 그대로이므로 과목 연결은 유지한다.
    public void changeCourseName(String courseName) {
        this.courseName = courseName;
    }

    public void changeTime(Weekday dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void changeBuilding(Building building) {
        this.building = building;
    }

    public void changeRoom(String room) {
        this.room = room;
    }
}

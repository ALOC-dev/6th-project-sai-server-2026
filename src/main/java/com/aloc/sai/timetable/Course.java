package com.aloc.sai.timetable;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 학교 시간표 API로 동기화한 개설 과목(분반 단위). 사용자가 직접 만들지 않는다.
@Entity
@Table(name = "courses",
        uniqueConstraints = @UniqueConstraint(name = "uk_course_term_subject_class",
                columnNames = {"academicYear", "semester", "subjectNo", "dvclNo"}),
        indexes = @Index(name = "idx_course_term", columnList = "academicYear, semester"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long courseId;

    @Column(nullable = false)
    private Integer academicYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Semester semester;

    // 학교 교과목번호
    @Column(nullable = false, length = 20)
    private String subjectNo;

    // 분반번호
    @Column(nullable = false, length = 10)
    private String dvclNo;

    @Column(nullable = false, length = 100)
    private String courseName;

    // 마지막으로 학교 데이터와 맞춘 시각
    @Column(nullable = false)
    private LocalDateTime syncedAt;

    // 강의시간 문자열을 파싱한 요일별 수업 시간. 원격·시간 미정 과목은 비어 있다.
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseSchedule> schedules = new ArrayList<>();

    public Course(AcademicTerm term, String subjectNo, String dvclNo, String courseName, LocalDateTime syncedAt) {
        this.academicYear = term.academicYear();
        this.semester = term.semester();
        this.subjectNo = subjectNo;
        this.dvclNo = dvclNo;
        this.courseName = courseName;
        this.syncedAt = syncedAt;
    }

    public void addSchedule(CourseSchedule schedule) {
        schedules.add(schedule);
    }
}

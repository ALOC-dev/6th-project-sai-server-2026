package com.aloc.sai.timetable;

import com.aloc.sai.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// 사용자의 학기별 시간표. 수업을 처음 등록할 때 자동으로 만든다.
@Entity
@Table(name = "timetables",
        uniqueConstraints = @UniqueConstraint(name = "uk_timetable_user_term",
                columnNames = {"user_id", "academicYear", "semester"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Timetable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long timetableId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Integer academicYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Semester semester;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 항목을 추가·수정·삭제할 때마다 갱신한다. FE가 시간표 캐시를 다시 받을지 판단하는 기준이다.
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Timetable(User user, AcademicTerm term, LocalDateTime now) {
        this.user = user;
        this.academicYear = term.academicYear();
        this.semester = term.semester();
        this.updatedAt = now;
    }

    public boolean isOwnedBy(Long userId) {
        return user.getUserId().equals(userId);
    }

    public void touch(LocalDateTime now) {
        this.updatedAt = now;
    }
}

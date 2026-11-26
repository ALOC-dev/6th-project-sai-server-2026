package com.aloc.sai.course;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    // 과목명 또는 교과목번호 부분 검색. 수업 시간은 개수 제한이 정확하도록 따로 조회한다.
    @Query("""
            select c from Course c
            where c.academicYear = :academicYear and c.semester = :semester
              and (c.courseName like :pattern escape '\\' or c.subjectNo like :pattern escape '\\')
            order by c.courseName, c.subjectNo, c.dvclNo
            """)
    List<Course> search(@Param("academicYear") int academicYear,
                        @Param("semester") Semester semester,
                        @Param("pattern") String pattern,
                        Pageable pageable);

    boolean existsByAcademicYearAndSemester(int academicYear, Semester semester);

    // 동기화용. 수업 시간·건물까지 한 번에 가져와 과목마다 추가 조회가 생기지 않게 한다.
    @Query("""
            select distinct c from Course c
            left join fetch c.schedules s
            left join fetch s.building
            where c.academicYear = :academicYear and c.semester = :semester
            """)
    List<Course> findAllWithSchedulesByTerm(@Param("academicYear") int academicYear,
                                            @Param("semester") Semester semester);
}

package com.aloc.sai.timetable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TimetableRepository extends JpaRepository<Timetable, Long> {
    Optional<Timetable> findByUserUserIdAndAcademicYearAndSemester(Long userId, Integer academicYear, Semester semester);

    @Modifying
    @Query("delete from Timetable t where t.user.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}

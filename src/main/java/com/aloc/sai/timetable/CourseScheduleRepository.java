package com.aloc.sai.timetable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CourseScheduleRepository extends JpaRepository<CourseSchedule, Long> {

    @Query("""
            select s from CourseSchedule s
            left join fetch s.building
            where s.course.courseId in :courseIds
            """)
    List<CourseSchedule> findAllWithBuildingByCourseIdIn(@Param("courseIds") Collection<Long> courseIds);
}

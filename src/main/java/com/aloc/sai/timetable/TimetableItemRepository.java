package com.aloc.sai.timetable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface TimetableItemRepository extends JpaRepository<TimetableItem, Long> {

    @Query("""
            select i from TimetableItem i
            left join fetch i.course
            left join fetch i.building
            where i.timetable.timetableId = :timetableId
            """)
    List<TimetableItem> findAllWithDetailsByTimetableId(@Param("timetableId") Long timetableId);

    @Query("""
            select i from TimetableItem i
            join fetch i.timetable
            left join fetch i.course
            left join fetch i.building
            where i.timetableItemId = :timetableItemId
            """)
    Optional<TimetableItem> findWithDetailsById(@Param("timetableItemId") Long timetableItemId);

    // 같은 요일에 [start, end)가 겹치는 수업이 있는지 본다. 끝나는 시각에 바로 시작하는 수업은 겹치지 않는다.
    // 수정할 때는 자기 자신을 빼고 본다(excludeItemId). 새로 등록할 때는 null이다.
    @Query("""
            select count(i) > 0 from TimetableItem i
            where i.timetable.timetableId = :timetableId
              and i.dayOfWeek = :dayOfWeek
              and i.startTime < :endTime and i.endTime > :startTime
              and (:excludeItemId is null or i.timetableItemId <> :excludeItemId)
            """)
    boolean existsOverlap(@Param("timetableId") Long timetableId,
                          @Param("dayOfWeek") Weekday dayOfWeek,
                          @Param("startTime") LocalTime startTime,
                          @Param("endTime") LocalTime endTime,
                          @Param("excludeItemId") Long excludeItemId);

    boolean existsByTimetableTimetableIdAndCourseCourseId(Long timetableId, Long courseId);

    @Modifying
    @Query("delete from TimetableItem i where i.timetable.timetableId in "
            + "(select t.timetableId from Timetable t where t.user.userId = :userId)")
    void deleteAllByUserId(@Param("userId") Long userId);
}

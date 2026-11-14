package com.aloc.sai.timetable;

import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.timetable.dto.BuildingListResponse;
import com.aloc.sai.timetable.dto.CourseSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 학교 데이터(건물·개설 과목) 조회. 데이터는 학교 API 동기화로 채우고, 여기서는 읽기만 한다.
@Service
@RequiredArgsConstructor
public class CourseService {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final Comparator<CourseSchedule> SCHEDULE_ORDER =
            Comparator.comparing(CourseSchedule::getDayOfWeek).thenComparing(CourseSchedule::getStartTime);

    private final BuildingRepository buildingRepository;
    private final CourseRepository courseRepository;
    private final CourseScheduleRepository courseScheduleRepository;

    @Transactional(readOnly = true)
    public BuildingListResponse getBuildings(String keyword) {
        String trimmed = keyword == null ? "" : keyword.strip();
        List<Building> buildings = trimmed.isEmpty()
                ? buildingRepository.findAllByOrderByBuildingNameAsc()
                : buildingRepository.searchByName(containsPattern(trimmed));
        return new BuildingListResponse(buildings.stream().map(BuildingListResponse.BuildingItem::from).toList());
    }

    // 학기를 보내지 않으면 현재 학기(KST)에서 찾는다.
    @Transactional(readOnly = true)
    public CourseSearchResponse searchCourses(String keyword, Integer academicYear, Semester semester, Integer size) {
        String trimmed = keyword == null ? "" : keyword.strip();
        if (trimmed.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "검색어를 입력해 주세요.");
        }
        int limit = size == null ? DEFAULT_SIZE : size;
        if (limit < 1 || limit > MAX_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "size는 1 이상 50 이하여야 합니다.");
        }
        AcademicTerm term = resolveTerm(academicYear, semester);

        List<Course> courses = courseRepository.search(
                term.academicYear(), term.semester(), containsPattern(trimmed), PageRequest.of(0, limit));
        Map<Long, List<CourseSchedule>> schedulesByCourse = courses.isEmpty() ? Map.of()
                : courseScheduleRepository.findAllWithBuildingByCourseIdIn(courses.stream().map(Course::getCourseId).toList())
                .stream()
                .sorted(SCHEDULE_ORDER)
                .collect(Collectors.groupingBy(schedule -> schedule.getCourse().getCourseId()));

        return new CourseSearchResponse(courses.stream()
                .map(course -> CourseSearchResponse.CourseItem.of(
                        course, schedulesByCourse.getOrDefault(course.getCourseId(), List.of())))
                .toList());
    }

    // 둘 다 없으면 현재 학기, 둘 다 있으면 그 학기다. 하나만 보내면 어느 학기인지 알 수 없어 400이다.
    public static AcademicTerm resolveTerm(Integer academicYear, Semester semester) {
        if (academicYear == null && semester == null) {
            return AcademicTerm.current();
        }
        if (academicYear == null || semester == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "academicYear와 semester는 함께 보내야 합니다.");
        }
        return new AcademicTerm(academicYear, semester);
    }

    // 사용자가 입력한 %, _ 가 와일드카드로 해석되지 않도록 이스케이프한다.
    private static String containsPattern(String keyword) {
        String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}

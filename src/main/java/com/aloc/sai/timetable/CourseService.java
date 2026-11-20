package com.aloc.sai.timetable;

import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.timetable.dto.BuildingListResponse;
import com.aloc.sai.timetable.dto.CourseSearchResponse;
import com.aloc.sai.timetable.dto.TimetableRecognitionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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

    // 이미지에서 읽은 수업을 그 학기 학교 과목·건물과 맞춘다.
    // 과목: 이름(공백·대소문자 무시)이 같고 같은 요일에 시간이 겹치는 수업 시간이 있는 과목이 정확히 하나면 매칭하고,
    //       시간·강의실·건물은 학교 데이터 값으로 보정한다. 같은 이름의 분반이 여럿 남으면 잘못 연결하지 않도록 매칭하지 않는다.
    // 건물: 과목으로 정해지지 않으면 읽은 건물명(공백 무시)과 이름이 같은 건물이 정확히 하나일 때 매칭한다.
    //       건물이 정해지면 건물명도 그 건물의 이름으로 줘서 ID와 이름이 서로 다른 건물을 가리키지 않게 한다.
    // 과목명·요일·시간이 빠졌거나 시작 ≥ 종료인 수업은 등록할 수 없으므로 버린다.
    // 인식은 사용자당 학기에 한두 번이라, 그 학기 과목을 한 번에 읽어 메모리에서 맞춘다.
    @Transactional(readOnly = true)
    public List<TimetableRecognitionResponse.RecognizedItem> matchRecognized(AcademicTerm term, List<RecognizedClass> classes) {
        List<RecognizedClass> valid = classes.stream().filter(CourseService::isUsable).toList();
        if (valid.isEmpty()) {
            return List.of();
        }
        Map<String, List<Course>> coursesByName = courseRepository
                .findAllWithSchedulesByTerm(term.academicYear(), term.semester()).stream()
                .collect(Collectors.groupingBy(course -> matchKey(course.getCourseName())));
        Map<String, List<Building>> buildingsByName = buildingRepository.findAll().stream()
                .collect(Collectors.groupingBy(building -> matchKey(building.getBuildingName())));

        return valid.stream()
                .map(recognized -> toRecognizedItem(recognized, coursesByName, buildingsByName))
                .sorted(Comparator.comparing(TimetableRecognitionResponse.RecognizedItem::dayOfWeek)
                        .thenComparing(TimetableRecognitionResponse.RecognizedItem::startTime))
                .toList();
    }

    private static TimetableRecognitionResponse.RecognizedItem toRecognizedItem(
            RecognizedClass recognized, Map<String, List<Course>> coursesByName, Map<String, List<Building>> buildingsByName) {
        String buildingName = blankToNull(recognized.buildingName());
        Building buildingByName = buildingName == null ? null
                : single(buildingsByName.getOrDefault(matchKey(buildingName), List.of()));

        List<CourseSchedule> matches = coursesByName.getOrDefault(matchKey(recognized.courseName()), List.of()).stream()
                .flatMap(course -> course.getSchedules().stream())
                .filter(schedule -> schedule.getDayOfWeek() == recognized.dayOfWeek()
                        && schedule.getStartTime().isBefore(recognized.endTime())
                        && recognized.startTime().isBefore(schedule.getEndTime()))
                .toList();
        boolean oneCourse = !matches.isEmpty()
                && matches.stream().map(schedule -> schedule.getCourse().getCourseId()).distinct().count() == 1;

        if (!oneCourse) {
            return new TimetableRecognitionResponse.RecognizedItem(null, null, null,
                    recognized.courseName().strip(), recognized.dayOfWeek(), recognized.startTime(), recognized.endTime(),
                    buildingByName == null ? null : buildingByName.getBuildingId(),
                    buildingByName == null ? buildingName : buildingByName.getBuildingName(),
                    blankToNull(recognized.room()), recognized.confidence());
        }
        CourseSchedule schedule = matches.getFirst();
        Course course = schedule.getCourse();
        Building building = schedule.getBuilding() != null ? schedule.getBuilding() : buildingByName;
        return new TimetableRecognitionResponse.RecognizedItem(course.getCourseId(), course.getSubjectNo(), course.getDvclNo(),
                course.getCourseName(), schedule.getDayOfWeek(), schedule.getStartTime(), schedule.getEndTime(),
                building == null ? null : building.getBuildingId(),
                building == null ? buildingName : building.getBuildingName(),
                schedule.getRoom() != null ? schedule.getRoom() : blankToNull(recognized.room()),
                recognized.confidence());
    }

    private static boolean isUsable(RecognizedClass recognized) {
        return recognized != null && blankToNull(recognized.courseName()) != null && recognized.dayOfWeek() != null
                && recognized.startTime() != null && recognized.endTime() != null
                && recognized.startTime().isBefore(recognized.endTime());
    }

    // OCR은 띄어쓰기를 자주 틀리므로 공백을 모두 빼고, 영문 대소문자를 무시해 비교한다.
    private static String matchKey(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static <T> T single(List<T> candidates) {
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
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

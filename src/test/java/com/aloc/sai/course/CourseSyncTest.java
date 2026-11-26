package com.aloc.sai.course;

import com.aloc.sai.course.UosApiClient.UosCourse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CourseSyncTest {
    private static final AcademicTerm TERM = new AcademicTerm(2026, Semester.SECOND);
    private static final LocalDateTime FIRST_SYNC = LocalDateTime.of(2026, 11, 1, 5, 0);
    private static final LocalDateTime SECOND_SYNC = LocalDateTime.of(2026, 11, 2, 5, 0);

    @Autowired
    private CourseSyncService courseSyncService;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private CourseRepository courseRepository;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        courseRepository.deleteAll();
        buildingRepository.deleteAll();
    }

    @Test
    void 공식_건물_번호표로_건물을_만들고_다시_돌려도_중복되지_않는다() {
        courseSyncService.syncBuildings();
        courseSyncService.syncBuildings();

        List<Building> buildings = buildingRepository.findAll();
        assertThat(buildings).hasSize(CampusBuildings.NAME_BY_NUMBER.size());
        assertThat(buildings).anySatisfy(building -> {
            assertThat(building.getBuildingCode()).isEqualTo("19");
            assertThat(building.getBuildingName()).isEqualTo("정보기술관");
        });
    }

    @Test
    void 번호_없이_먼저_있던_같은_이름의_건물에는_번호를_채운다() {
        Building existing = buildingRepository.save(new Building("정보기술관", null, 37.5, 127.0));

        courseSyncService.syncBuildings();

        Building synced = buildingRepository.findById(existing.getBuildingId()).orElseThrow();
        assertThat(synced.getBuildingCode()).isEqualTo("19");
        assertThat(synced.getLatitude()).isEqualTo(37.5);
        assertThat(buildingRepository.findAll()).hasSize(CampusBuildings.NAME_BY_NUMBER.size());
    }

    @Test
    void 과목과_수업_시간을_저장하고_강의실을_건물과_연결한다() {
        courseSyncService.syncBuildings();

        CourseSyncService.SyncResult result = courseSyncService.saveCourses(TERM, List.of(
                new UosCourse("01234", "01", "자료구조  ", "월[8,9]/19-B102,수[8]/33-402,금[1]/2-101"),
                new UosCourse("05678", "02", "원격강의", null),
                new UosCourse("09999", "01", "형식이상과목", "아무 말"),
                new UosCourse("01234", "01", "중복된 분반", "화[1]/1-101"),
                new UosCourse("", "01", "번호 없는 과목", "화[1]/1-101")
        ), FIRST_SYNC);

        assertThat(result).isEqualTo(new CourseSyncService.SyncResult(3, 0, 2, 2));
        Map<String, Course> courses = findCourses();
        assertThat(courses).containsOnlyKeys("01234", "05678", "09999");
        assertThat(courses.get("05678").getSchedules()).isEmpty();
        assertThat(courses.get("09999").getSchedules()).isEmpty();

        Course course = courses.get("01234");
        assertThat(course.getCourseName()).isEqualTo("자료구조");
        assertThat(course.getSyncedAt()).isEqualTo(FIRST_SYNC);
        List<CourseSchedule> schedules = sorted(course.getSchedules());
        assertThat(schedules).hasSize(3);
        assertThat(schedules.get(0).getDayOfWeek()).isEqualTo(Weekday.MON);
        assertThat(schedules.get(0).getStartTime()).isEqualTo(LocalTime.of(16, 0));
        assertThat(schedules.get(0).getEndTime()).isEqualTo(LocalTime.of(17, 50));
        assertThat(schedules.get(0).getBuilding().getBuildingName()).isEqualTo("정보기술관");
        assertThat(schedules.get(0).getRoom()).isEqualTo("B102");
        assertThat(schedules.get(1).getBuilding().getBuildingName()).isEqualTo("미래관");
        // 번호표에 없는 건물은 비워 두고 호실만 남긴다.
        assertThat(schedules.get(2).getBuilding()).isNull();
        assertThat(schedules.get(2).getRoom()).isEqualTo("101");
    }

    @Test
    void 다시_동기화하면_바뀐_과목만_갱신하고_빠진_과목은_남긴다() {
        courseSyncService.syncBuildings();
        courseSyncService.saveCourses(TERM, List.of(
                new UosCourse("01234", "01", "자료구조", "월[8,9]/19-B102"),
                new UosCourse("05678", "01", "운영체제", "화[1,2]/19-201"),
                new UosCourse("07777", "01", "폐강과목", "목[1]/1-101")
        ), FIRST_SYNC);
        Long unchangedScheduleId = findCourses().get("01234").getSchedules().getFirst().getCourseScheduleId();

        CourseSyncService.SyncResult result = courseSyncService.saveCourses(TERM, List.of(
                new UosCourse("01234", "01", "자료구조", "월[8,9]/19-B102"),
                new UosCourse("05678", "01", "운영체제(영어)", "수[3]/33-101"),
                new UosCourse("08888", "01", "신설과목", null)
        ), SECOND_SYNC);

        assertThat(result).isEqualTo(new CourseSyncService.SyncResult(1, 2, 0, 1));
        Map<String, Course> courses = findCourses();
        assertThat(courses).containsOnlyKeys("01234", "05678", "07777", "08888");

        Course unchanged = courses.get("01234");
        assertThat(unchanged.getSyncedAt()).isEqualTo(SECOND_SYNC);
        assertThat(unchanged.getSchedules()).singleElement()
                .satisfies(schedule -> assertThat(schedule.getCourseScheduleId()).isEqualTo(unchangedScheduleId));

        Course changed = courses.get("05678");
        assertThat(changed.getCourseName()).isEqualTo("운영체제(영어)");
        assertThat(changed.getSchedules()).singleElement().satisfies(schedule -> {
            assertThat(schedule.getDayOfWeek()).isEqualTo(Weekday.WED);
            assertThat(schedule.getStartTime()).isEqualTo(LocalTime.of(11, 0));
            assertThat(schedule.getBuilding().getBuildingName()).isEqualTo("미래관");
        });

        assertThat(courses.get("07777").getSyncedAt()).isEqualTo(FIRST_SYNC);
    }

    @Test
    void 학기가_다르면_같은_과목번호도_따로_저장한다() {
        courseSyncService.saveCourses(TERM, List.of(new UosCourse("01234", "01", "자료구조", null)), FIRST_SYNC);
        courseSyncService.saveCourses(TERM.next(), List.of(new UosCourse("01234", "01", "자료구조", null)), FIRST_SYNC);

        assertThat(courseRepository.findAll()).hasSize(2);
        assertThat(courseSyncService.hasCourses(TERM)).isTrue();
        assertThat(courseSyncService.hasCourses(new AcademicTerm(2025, Semester.FIRST))).isFalse();
    }

    private Map<String, Course> findCourses() {
        return courseRepository.findAllWithSchedulesByTerm(TERM.academicYear(), TERM.semester()).stream()
                .collect(Collectors.toMap(Course::getSubjectNo, Function.identity()));
    }

    private static List<CourseSchedule> sorted(List<CourseSchedule> schedules) {
        return schedules.stream()
                .sorted(Comparator.comparing(CourseSchedule::getDayOfWeek).thenComparing(CourseSchedule::getStartTime))
                .toList();
    }
}

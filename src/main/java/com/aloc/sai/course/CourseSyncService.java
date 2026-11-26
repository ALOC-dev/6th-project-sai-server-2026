package com.aloc.sai.course;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// 학교 데이터(건물·개설 과목)를 DB에 맞춘다. 학교 API 호출은 트랜잭션 밖(CourseSyncScheduler)에서 한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseSyncService {
    private static final int MAX_SUBJECT_NO_LENGTH = 20;
    private static final int MAX_DVCL_NO_LENGTH = 10;
    private static final int MAX_COURSE_NAME_LENGTH = 100;

    private final BuildingRepository buildingRepository;
    private final CourseRepository courseRepository;

    public record SyncResult(int created, int updated, int skipped, int withoutSchedule) {
    }

    @Transactional(readOnly = true)
    public boolean hasCourses(AcademicTerm term) {
        return courseRepository.existsByAcademicYearAndSemester(term.academicYear(), term.semester());
    }

    // 캠퍼스 공식 건물 번호표로 건물을 맞춘다. 번호가 없는 기존 건물은 이름이 같으면 번호를 채운다.
    @Transactional
    public void syncBuildings() {
        List<Building> buildings = buildingRepository.findAll();
        Map<String, Building> byCode = buildings.stream()
                .filter(building -> building.getBuildingCode() != null)
                .collect(Collectors.toMap(Building::getBuildingCode, Function.identity()));
        Map<String, Building> byName = buildings.stream()
                .collect(Collectors.toMap(Building::getBuildingName, Function.identity()));

        CampusBuildings.NAME_BY_NUMBER.forEach((number, name) -> {
            Building building = byCode.getOrDefault(number, byName.get(name));
            if (building == null) {
                buildingRepository.save(new Building(name, number, null, null));
            } else if (!name.equals(building.getBuildingName()) || !number.equals(building.getBuildingCode())) {
                building.syncFromSchool(name, number);
            }
        });
    }

    // 학교 API에서 받은 한 학기 과목을 저장한다. 응답에서 빠진 과목은 시간표 항목이 참조할 수 있어 지우지 않는다.
    // 강의시간이 없거나 해석하지 못한 과목은 수업 시간 없이 저장한다.
    @Transactional
    public SyncResult saveCourses(AcademicTerm term, List<UosApiClient.UosCourse> rows, LocalDateTime syncedAt) {
        Map<String, Building> buildingsByNumber = buildingRepository.findAll().stream()
                .filter(building -> building.getBuildingCode() != null)
                .collect(Collectors.toMap(Building::getBuildingCode, Function.identity()));
        Map<CourseKey, Course> existing = new HashMap<>();
        for (Course course : courseRepository.findAllWithSchedulesByTerm(term.academicYear(), term.semester())) {
            existing.put(new CourseKey(course.getSubjectNo(), course.getDvclNo()), course);
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        int withoutSchedule = 0;
        Set<CourseKey> seen = new HashSet<>();
        for (UosApiClient.UosCourse row : rows) {
            String subjectNo = strip(row.subjectNo());
            String dvclNo = strip(row.dvclNo());
            String courseName = strip(row.courseName());
            if (!isValid(subjectNo, dvclNo, courseName) || !seen.add(new CourseKey(subjectNo, dvclNo))) {
                skipped++;
                continue;
            }

            Set<ClassTimeParser.ClassTime> classTimes = parseClassTime(row);
            if (classTimes.isEmpty()) {
                withoutSchedule++;
            }

            Course course = existing.get(new CourseKey(subjectNo, dvclNo));
            if (course == null) {
                course = new Course(term, subjectNo, dvclNo, courseName, syncedAt);
                for (CourseSchedule schedule : toSchedules(course, classTimes, buildingsByNumber)) {
                    course.addSchedule(schedule);
                }
                courseRepository.save(course);
                created++;
                continue;
            }

            course.syncFromSchool(courseName, syncedAt);
            if (!sameSchedules(course.getSchedules(), classTimes, buildingsByNumber)) {
                course.replaceSchedules(toSchedules(course, classTimes, buildingsByNumber));
            }
            updated++;
        }
        return new SyncResult(created, updated, skipped, withoutSchedule);
    }

    private Set<ClassTimeParser.ClassTime> parseClassTime(UosApiClient.UosCourse row) {
        try {
            return new LinkedHashSet<>(ClassTimeParser.parse(row.classTime()));
        } catch (IllegalArgumentException e) {
            log.warn("강의시간을 해석하지 못해 수업 시간 없이 저장합니다. subjectNo={}, dvclNo={}, classTime={}",
                    row.subjectNo(), row.dvclNo(), row.classTime());
            return Set.of();
        }
    }

    private static List<CourseSchedule> toSchedules(Course course, Set<ClassTimeParser.ClassTime> classTimes,
                                                    Map<String, Building> buildingsByNumber) {
        List<CourseSchedule> schedules = new ArrayList<>();
        for (ClassTimeParser.ClassTime time : classTimes) {
            Building building = time.buildingNumber() == null ? null : buildingsByNumber.get(time.buildingNumber());
            schedules.add(new CourseSchedule(course, time.dayOfWeek(), time.startTime(), time.endTime(),
                    building, time.room()));
        }
        return schedules;
    }

    // 수업 시간이 그대로면 바꾸지 않아 매일 동기화할 때 불필요한 삭제·삽입이 생기지 않게 한다.
    private static boolean sameSchedules(List<CourseSchedule> current, Set<ClassTimeParser.ClassTime> classTimes,
                                         Map<String, Building> buildingsByNumber) {
        Set<ScheduleKey> currentKeys = current.stream()
                .map(schedule -> new ScheduleKey(schedule.getDayOfWeek(), schedule.getStartTime().toString(),
                        schedule.getEndTime().toString(),
                        schedule.getBuilding() == null ? null : schedule.getBuilding().getBuildingId(),
                        schedule.getRoom()))
                .collect(Collectors.toSet());
        Set<ScheduleKey> newKeys = classTimes.stream()
                .map(time -> {
                    Building building = time.buildingNumber() == null ? null
                            : buildingsByNumber.get(time.buildingNumber());
                    return new ScheduleKey(time.dayOfWeek(), time.startTime().toString(), time.endTime().toString(),
                            building == null ? null : building.getBuildingId(), time.room());
                })
                .collect(Collectors.toSet());
        return current.size() == classTimes.size() && currentKeys.equals(newKeys);
    }

    private static boolean isValid(String subjectNo, String dvclNo, String courseName) {
        return subjectNo != null && !subjectNo.isEmpty() && subjectNo.length() <= MAX_SUBJECT_NO_LENGTH
                && dvclNo != null && !dvclNo.isEmpty() && dvclNo.length() <= MAX_DVCL_NO_LENGTH
                && courseName != null && !courseName.isEmpty() && courseName.length() <= MAX_COURSE_NAME_LENGTH;
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    private record CourseKey(String subjectNo, String dvclNo) {
    }

    private record ScheduleKey(Weekday dayOfWeek, String startTime, String endTime, Long buildingId, String room) {
    }
}

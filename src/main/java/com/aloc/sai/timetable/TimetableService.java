package com.aloc.sai.timetable;

import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.course.AcademicTerm;
import com.aloc.sai.course.Building;
import com.aloc.sai.course.Course;
import com.aloc.sai.course.CourseSchedule;
import com.aloc.sai.course.CourseService;
import com.aloc.sai.course.Semester;
import com.aloc.sai.course.Weekday;
import com.aloc.sai.timetable.dto.TimetableItemBulkCreateRequest;
import com.aloc.sai.timetable.dto.TimetableItemCreateRequest;
import com.aloc.sai.timetable.dto.TimetableItemCreateResponse;
import com.aloc.sai.timetable.dto.TimetableItemResponse;
import com.aloc.sai.timetable.dto.TimetableItemUpdateRequest;
import com.aloc.sai.timetable.dto.TimetableItemUpdateResponse;
import com.aloc.sai.timetable.dto.TimetableResponse;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserService;
import com.aloc.sai.user.UserWithdrawEvent;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class TimetableService {
    private static final int COURSE_NAME_MAX_LENGTH = 50;
    private static final int ROOM_MAX_LENGTH = 50;
    // 한 학기 시간표는 많아야 수십 개다. 잠금을 쥔 채 처리하므로 지나치게 큰 요청을 막는다.
    private static final int BULK_MAX_ITEMS = 50;
    private static final Comparator<TimetableItem> ITEM_ORDER =
            Comparator.comparing(TimetableItem::getDayOfWeek).thenComparing(TimetableItem::getStartTime);

    private final UserService userService;
    private final TimetableRepository timetableRepository;
    private final TimetableItemRepository timetableItemRepository;
    private final CourseService courseService;

    // today=true면 현재 학기에서 오늘 요일(KST) 수업만 시작 시각순으로 준다. 학기와 함께 보내면 어느 학기인지 모호해 400이다.
    @Transactional(readOnly = true)
    public TimetableResponse getMyTimetable(Long userId, Integer academicYear, Semester semester, boolean today) {
        if (today && (academicYear != null || semester != null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "today와 academicYear·semester는 함께 보낼 수 없습니다.");
        }
        Weekday todayWeekday = today ? Weekday.from(LocalDate.now(AcademicTerm.KST).getDayOfWeek()) : null;
        return findTimetable(userId, CourseService.resolveTerm(academicYear, semester), todayWeekday);
    }

    // 다른 사용자의 학기별 시간표. 친구인지는 확인하지 않으므로 호출하는 쪽(FriendService)이 먼저 확인해야 한다.
    @Transactional(readOnly = true)
    public TimetableResponse getTimetableOf(Long userId, Integer academicYear, Semester semester) {
        return findTimetable(userId, CourseService.resolveTerm(academicYear, semester), null);
    }

    // todayWeekday가 있으면 그 요일 수업만 준다.
    private TimetableResponse findTimetable(Long userId, AcademicTerm term, Weekday todayWeekday) {
        return timetableRepository.findByUserUserIdAndAcademicYearAndSemester(userId, term.academicYear(), term.semester())
                .map(timetable -> TimetableResponse.of(timetable,
                        timetableItemRepository.findAllWithDetailsByTimetableId(timetable.getTimetableId()).stream()
                                .filter(item -> todayWeekday == null || item.getDayOfWeek() == todayWeekday)
                                .sorted(ITEM_ORDER)
                                .map(TimetableItemResponse::from)
                                .toList()))
                .orElseGet(() -> TimetableResponse.empty(term));
    }

    // 여러 사용자의 그날(KST 날짜 기준 학기·요일) 수업을 사용자별로 시작 시각순으로 준다. 수업이 없는 사용자는 키가 없다.
    @Transactional(readOnly = true)
    public Map<Long, List<TimetableItem>> getClassesOn(Collection<Long> userIds, LocalDate date) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        AcademicTerm term = AcademicTerm.of(date);
        return timetableItemRepository.findAllByUserIdsAndTermAndDay(
                        userIds, term.academicYear(), term.semester(), Weekday.from(date.getDayOfWeek()))
                .stream()
                .collect(Collectors.groupingBy(item -> item.getTimetable().getUser().getUserId(),
                        LinkedHashMap::new, Collectors.toList()));
    }

    // 같은 사용자의 시간표 쓰기는 사용자 행을 잠그고 차례로 처리한다.
    // 그래서 시간표 자동 생성(UK)이 동시에 겹치거나, 겹침 검사를 둘 다 통과해 같은 시간에 수업이 두 개 생기는 일이 없다.
    // 탈퇴도 같은 행을 잠그므로 탈퇴와 겹쳐도 FK 위반 없이 차례로 처리된다.
    @Transactional
    public TimetableItemCreateResponse addItem(Long userId, TimetableItemCreateRequest request) {
        User user = userService.getUserForUpdate(userId);

        if (request.courseId() != null && request.hasManualFields()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "과목 선택 등록과 직접 입력은 함께 보낼 수 없습니다.");
        }
        List<TimetableItem> created = request.courseId() != null
                ? addCourse(user, request.courseId())
                : List.of(addManualItem(user, request));

        Timetable timetable = created.getFirst().getTimetable();
        return new TimetableItemCreateResponse(timetable.getTimetableId(),
                created.stream().sorted(ITEM_ORDER).map(TimetableItemResponse::from).toList());
    }

    // 이미지 인식 결과를 한 번에 저장한다. 수업이 하나도 없는 시간표에만 쓸 수 있어 기존 수업과의 겹침은 볼 필요가 없고,
    // 요청 항목끼리만 겹침을 본다. 하나라도 잘못되면 아무것도 만들지 않는다.
    // 사용자 행을 잠그므로 같은 사용자의 일괄 등록이 동시에 와도 둘 다 빈 시간표 검사를 통과하지 않는다.
    @Transactional
    public TimetableItemCreateResponse addItems(Long userId, TimetableItemBulkCreateRequest request) {
        if (request.academicYear() == null || request.semester() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "연도와 학기를 입력해 주세요.");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "등록할 수업을 1개 이상 보내 주세요.");
        }
        if (request.items().size() > BULK_MAX_ITEMS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "한 번에 등록할 수 있는 수업은 50개까지입니다.");
        }
        AcademicTerm term = new AcademicTerm(request.academicYear(), request.semester());
        // 과목·건물은 항목마다 조회하지 않고 잠그기 전에 한 번에 읽는다.
        Map<Long, Course> courses = findAllByIds(request.items().stream()
                .map(item -> item == null ? null : item.courseId()), courseService::findCourses, Course::getCourseId);
        Map<Long, Building> buildings = findAllByIds(request.items().stream()
                .map(item -> item == null ? null : item.buildingId()), courseService::findBuildings, Building::getBuildingId);
        User user = userService.getUserForUpdate(userId);

        Timetable timetable = findOrCreateTimetable(user, term);
        if (timetable.getTimetableId() != null && timetableItemRepository.existsByTimetableTimetableId(timetable.getTimetableId())) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 등록된 수업이 있는 시간표에는 일괄 등록할 수 없습니다.");
        }

        List<TimetableItem> items = new ArrayList<>();
        for (TimetableItemBulkCreateRequest.BulkItem bulkItem : request.items()) {
            TimetableItem item = toBulkItem(timetable, term, bulkItem, courses, buildings);
            if (items.stream().anyMatch(other -> overlaps(other, item))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "보낸 수업끼리 같은 요일에 시간이 겹칩니다.");
            }
            items.add(item);
        }
        timetable.touch(LocalDateTime.now());
        return new TimetableItemCreateResponse(timetable.getTimetableId(),
                timetableItemRepository.saveAll(items).stream().sorted(ITEM_ORDER).map(TimetableItemResponse::from).toList());
    }

    @Transactional
    public TimetableItemUpdateResponse updateItem(Long userId, Long timetableItemId, TimetableItemUpdateRequest request) {
        if (request.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "수정할 항목이 없습니다.");
        }
        userService.getUserForUpdate(userId);
        TimetableItem item = findMyItem(userId, timetableItemId);

        if (request.courseName() != null) {
            item.changeCourseName(normalizeCourseName(request.courseName()));
        }
        if (request.dayOfWeek() != null || request.startTime() != null || request.endTime() != null) {
            Weekday dayOfWeek = request.dayOfWeek() != null ? request.dayOfWeek() : item.getDayOfWeek();
            LocalTime startTime = request.startTime() != null ? request.startTime() : item.getStartTime();
            LocalTime endTime = request.endTime() != null ? request.endTime() : item.getEndTime();
            validateTime(startTime, endTime);
            checkOverlap(item.getTimetable(), dayOfWeek, startTime, endTime, item.getTimetableItemId());
            item.changeTime(dayOfWeek, startTime, endTime);
        }
        if (request.buildingId() != null) {
            item.changeBuilding(findBuilding(request.buildingId()));
        }
        if (request.room() != null) {
            item.changeRoom(normalizeRoom(request.room()));
        }

        item.getTimetable().touch(LocalDateTime.now());
        return TimetableItemUpdateResponse.from(item);
    }

    @Transactional
    public void deleteItem(Long userId, Long timetableItemId) {
        userService.getUserForUpdate(userId);
        TimetableItem item = findMyItem(userId, timetableItemId);
        item.getTimetable().touch(LocalDateTime.now());
        timetableItemRepository.delete(item);
    }

    // 탈퇴 트랜잭션 안에서 사용자 행을 지우기 전에 실행된다. DB 정리만 하고 예외를 삼키지 않는다.
    @EventListener
    @Transactional
    public void deleteAllOnWithdraw(UserWithdrawEvent event) {
        timetableItemRepository.deleteAllByUserId(event.userId());
        timetableRepository.deleteAllByUserId(event.userId());
    }

    // 학교 데이터의 모든 수업 시간을 항목으로 만든다. 학기도 과목 기준이다.
    private List<TimetableItem> addCourse(User user, Long courseId) {
        Course course = courseService.findCourse(courseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "과목을 찾을 수 없습니다."));
        if (course.getSchedules().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "수업 시간이 정해지지 않은 과목입니다. 직접 입력으로 등록해 주세요.");
        }

        Timetable timetable = findOrCreateTimetable(user, new AcademicTerm(course.getAcademicYear(), course.getSemester()));
        if (timetable.getTimetableId() != null
                && timetableItemRepository.existsByTimetableTimetableIdAndCourseCourseId(timetable.getTimetableId(), courseId)) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 시간표에 있는 과목입니다.");
        }

        List<TimetableItem> items = new ArrayList<>();
        for (CourseSchedule schedule : course.getSchedules()) {
            checkOverlap(timetable, schedule.getDayOfWeek(), schedule.getStartTime(), schedule.getEndTime(), null);
            // 이번에 만드는 항목은 아직 DB에 없으므로 서로도 비교한다. 학교 데이터에 같은 시간이 중복돼 있어도 겹치는 수업이 생기지 않게 한다.
            if (items.stream().anyMatch(item -> overlaps(item, schedule))) {
                throw new ApiException(HttpStatus.CONFLICT, "같은 시간에 이미 수업이 있습니다.");
            }
            items.add(new TimetableItem(timetable, course, course.getCourseName(), schedule.getDayOfWeek(),
                    schedule.getStartTime(), schedule.getEndTime(), schedule.getBuilding(), schedule.getRoom()));
        }
        timetable.touch(LocalDateTime.now());
        return timetableItemRepository.saveAll(items);
    }

    private TimetableItem addManualItem(User user, TimetableItemCreateRequest request) {
        if (request.academicYear() == null || request.semester() == null || request.courseName() == null
                || request.dayOfWeek() == null || request.startTime() == null || request.endTime() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "과목을 고르거나 연도·학기·과목명·요일·시작·종료 시간을 모두 입력해 주세요.");
        }
        String courseName = normalizeCourseName(request.courseName());
        validateTime(request.startTime(), request.endTime());
        String room = request.room() == null ? null : normalizeRoom(request.room());
        Building building = request.buildingId() == null ? null : findBuilding(request.buildingId());

        Timetable timetable = findOrCreateTimetable(user, new AcademicTerm(request.academicYear(), request.semester()));
        checkOverlap(timetable, request.dayOfWeek(), request.startTime(), request.endTime(), null);
        timetable.touch(LocalDateTime.now());
        return timetableItemRepository.save(new TimetableItem(timetable, null, courseName, request.dayOfWeek(),
                request.startTime(), request.endTime(), building, room));
    }

    // 일괄 등록의 항목 하나. 과목이 연결돼도 시간·강의실은 사용자가 보낸 값을 쓴다(인식 결과를 고쳤을 수 있다).
    // courses·buildings는 요청에 나온 아이디로 미리 읽어 둔 것이다. 없으면 404다.
    private TimetableItem toBulkItem(Timetable timetable, AcademicTerm term, TimetableItemBulkCreateRequest.BulkItem item,
                                     Map<Long, Course> courses, Map<Long, Building> buildings) {
        if (item == null || item.courseName() == null || item.dayOfWeek() == null
                || item.startTime() == null || item.endTime() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "수업마다 과목명·요일·시작·종료 시간을 모두 입력해 주세요.");
        }
        String courseName = normalizeCourseName(item.courseName());
        validateTime(item.startTime(), item.endTime());
        String room = item.room() == null ? null : normalizeRoom(item.room());
        Building building = null;
        if (item.buildingId() != null) {
            building = buildings.get(item.buildingId());
            if (building == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "건물을 찾을 수 없습니다.");
            }
        }
        Course course = null;
        if (item.courseId() != null) {
            course = courses.get(item.courseId());
            if (course == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "과목을 찾을 수 없습니다.");
            }
            // 다른 학기 과목을 연결하면 시간표 학기와 과목 학기가 어긋나므로 막는다.
            if (!course.getAcademicYear().equals(term.academicYear()) || course.getSemester() != term.semester()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "등록하려는 학기의 과목이 아닙니다.");
            }
        }
        return new TimetableItem(timetable, course, courseName, item.dayOfWeek(),
                item.startTime(), item.endTime(), building, room);
    }

    private static <T> Map<Long, T> findAllByIds(Stream<Long> ids, Function<Set<Long>, List<T>> finder,
                                                 Function<T, Long> idOf) {
        Set<Long> distinct = ids.filter(Objects::nonNull).collect(Collectors.toSet());
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return finder.apply(distinct).stream().collect(Collectors.toMap(idOf, Function.identity()));
    }

    private Timetable findOrCreateTimetable(User user, AcademicTerm term) {
        return timetableRepository.findByUserUserIdAndAcademicYearAndSemester(
                        user.getUserId(), term.academicYear(), term.semester())
                .orElseGet(() -> timetableRepository.save(new Timetable(user, term, LocalDateTime.now())));
    }

    // 본인 시간표의 항목만 수정·삭제할 수 있다.
    private TimetableItem findMyItem(Long userId, Long timetableItemId) {
        TimetableItem item = timetableItemRepository.findWithDetailsById(timetableItemId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "시간표 수업을 찾을 수 없습니다."));
        if (!item.getTimetable().isOwnedBy(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "내 시간표의 수업만 수정·삭제할 수 있습니다.");
        }
        return item;
    }

    private Building findBuilding(Long buildingId) {
        return courseService.findBuilding(buildingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "건물을 찾을 수 없습니다."));
    }

    private void checkOverlap(Timetable timetable, Weekday dayOfWeek, LocalTime startTime, LocalTime endTime,
                              Long excludeItemId) {
        if (timetable.getTimetableId() != null && timetableItemRepository.existsOverlap(
                timetable.getTimetableId(), dayOfWeek, startTime, endTime, excludeItemId)) {
            throw new ApiException(HttpStatus.CONFLICT, "같은 시간에 이미 수업이 있습니다.");
        }
    }

    // 같은 요일에서 [start, end)가 겹치는지 본다. DB 겹침 검사(existsOverlap)와 같은 기준이다.
    private static boolean overlaps(TimetableItem item, CourseSchedule schedule) {
        return item.getDayOfWeek() == schedule.getDayOfWeek()
                && item.getStartTime().isBefore(schedule.getEndTime())
                && schedule.getStartTime().isBefore(item.getEndTime());
    }

    private static boolean overlaps(TimetableItem item, TimetableItem other) {
        return item.getDayOfWeek() == other.getDayOfWeek()
                && item.getStartTime().isBefore(other.getEndTime())
                && other.getStartTime().isBefore(item.getEndTime());
    }

    private static void validateTime(LocalTime startTime, LocalTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "시작 시간은 종료 시간보다 빨라야 합니다.");
        }
    }

    private static String normalizeCourseName(String courseName) {
        String name = courseName.strip();
        int length = name.codePointCount(0, name.length());
        if (length < 1 || length > COURSE_NAME_MAX_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "과목명은 1자 이상 50자 이하여야 합니다.");
        }
        return name;
    }

    // 빈 문자열이면 강의실을 지운다.
    private static String normalizeRoom(String room) {
        String value = room.strip();
        if (value.isEmpty()) {
            return null;
        }
        if (value.codePointCount(0, value.length()) > ROOM_MAX_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "강의실은 50자 이하여야 합니다.");
        }
        return value;
    }
}

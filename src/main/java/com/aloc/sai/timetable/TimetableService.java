package com.aloc.sai.timetable;

import com.aloc.sai.common.exception.ApiException;
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
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TimetableService {
    private static final int COURSE_NAME_MAX_LENGTH = 50;
    private static final int ROOM_MAX_LENGTH = 50;
    private static final Comparator<TimetableItem> ITEM_ORDER =
            Comparator.comparing(TimetableItem::getDayOfWeek).thenComparing(TimetableItem::getStartTime);

    private final UserService userService;
    private final TimetableRepository timetableRepository;
    private final TimetableItemRepository timetableItemRepository;
    private final CourseRepository courseRepository;
    private final BuildingRepository buildingRepository;

    // today=true면 현재 학기에서 오늘 요일(KST) 수업만 시작 시각순으로 준다. 학기와 함께 보내면 어느 학기인지 모호해 400이다.
    @Transactional(readOnly = true)
    public TimetableResponse getMyTimetable(Long userId, Integer academicYear, Semester semester, boolean today) {
        if (today && (academicYear != null || semester != null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "today와 academicYear·semester는 함께 보낼 수 없습니다.");
        }
        AcademicTerm term = CourseService.resolveTerm(academicYear, semester);
        Weekday todayWeekday = today ? Weekday.from(LocalDate.now(AcademicTerm.KST).getDayOfWeek()) : null;

        return timetableRepository.findByUserUserIdAndAcademicYearAndSemester(userId, term.academicYear(), term.semester())
                .map(timetable -> TimetableResponse.of(timetable,
                        timetableItemRepository.findAllWithDetailsByTimetableId(timetable.getTimetableId()).stream()
                                .filter(item -> todayWeekday == null || item.getDayOfWeek() == todayWeekday)
                                .sorted(ITEM_ORDER)
                                .map(TimetableItemResponse::from)
                                .toList()))
                .orElseGet(() -> TimetableResponse.empty(term));
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
        Course course = courseRepository.findById(courseId)
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
        return buildingRepository.findById(buildingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "건물을 찾을 수 없습니다."));
    }

    private void checkOverlap(Timetable timetable, Weekday dayOfWeek, LocalTime startTime, LocalTime endTime,
                              Long excludeItemId) {
        if (timetable.getTimetableId() != null && timetableItemRepository.existsOverlap(
                timetable.getTimetableId(), dayOfWeek, startTime, endTime, excludeItemId)) {
            throw new ApiException(HttpStatus.CONFLICT, "같은 시간에 이미 수업이 있습니다.");
        }
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

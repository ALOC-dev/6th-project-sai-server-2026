package com.aloc.sai.timetable;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.auth.kakao.KakaoClient;
import com.aloc.sai.auth.kakao.KakaoUserInfo;
import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.timetable.dto.TimetableItemCreateRequest;
import com.aloc.sai.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TimetableTest {
    private static final AcademicTerm CURRENT = AcademicTerm.current();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TimetableRepository timetableRepository;

    @Autowired
    private TimetableItemRepository timetableItemRepository;

    @Autowired
    private TimetableService timetableService;

    @MockitoBean
    private KakaoClient kakaoClient;

    private String token;
    private String otherToken;
    private Long userId;
    private Building engineering;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        given(kakaoClient.getUserInfo("user-1")).willReturn(new KakaoUserInfo(1L, "지윤", null));
        given(kakaoClient.getUserInfo("user-2")).willReturn(new KakaoUserInfo(2L, "민수", null));
        token = login("user-1");
        otherToken = login("user-2");
        userId = userRepository.findByKakaoId(1L).orElseThrow().getUserId();
        engineering = buildingRepository.save(new Building("공학관", "B01", null, null));
    }

    // 시간표가 users를 참조하므로, 다른 테스트가 사용자를 지울 수 있게 정리하고 끝낸다.
    @AfterEach
    void cleanUp() {
        timetableItemRepository.deleteAll();
        timetableRepository.deleteAll();
        courseRepository.deleteAll();
        buildingRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---- 조회 ----

    @Test
    void 시간표가_없으면_현재_학기의_빈_시간표를_준다() throws Exception {
        getMe("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timetableId").value(nullValue()))
                .andExpect(jsonPath("$.academicYear").value(CURRENT.academicYear()))
                .andExpect(jsonPath("$.semester").value(CURRENT.semester().name()))
                .andExpect(jsonPath("$.updatedAt").value(nullValue()))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void 수업을_요일_시작시각순으로_조회한다() throws Exception {
        addManual(token, "TUE", "13:00", "14:15", "운영체제").andExpect(status().isCreated());
        addManual(token, "MON", "15:00", "16:15", "알고리즘").andExpect(status().isCreated());
        addManual(token, "MON", "09:00", "10:15", "자료구조").andExpect(status().isCreated());

        getMe("")
                .andExpect(jsonPath("$.timetableId").value(notNullValue()))
                .andExpect(jsonPath("$.updatedAt").value(notNullValue()))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].courseName").value("자료구조"))
                .andExpect(jsonPath("$.items[1].courseName").value("알고리즘"))
                .andExpect(jsonPath("$.items[2].courseName").value("운영체제"));
    }

    @Test
    void today면_오늘_요일_수업만_준다() throws Exception {
        Weekday today = Weekday.from(LocalDate.now(AcademicTerm.KST).getDayOfWeek());
        Weekday other = Weekday.values()[(today.ordinal() + 1) % 7];
        addManual(token, today.name(), "09:00", "10:15", "오늘 수업").andExpect(status().isCreated());
        addManual(token, other.name(), "09:00", "10:15", "다른 날 수업").andExpect(status().isCreated());

        getMe("?today=true")
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].courseName").value("오늘 수업"));
    }

    @Test
    void 다른_학기를_지정해_조회한다() throws Exception {
        AcademicTerm previous = new AcademicTerm(CURRENT.academicYear() - 1, CURRENT.semester());
        addManual(token, previous, "MON", "09:00", "10:15", "작년 수업").andExpect(status().isCreated());

        getMe("").andExpect(jsonPath("$.items", hasSize(0)));
        getMe("?academicYear=" + previous.academicYear() + "&semester=" + previous.semester())
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void today와_학기를_함께_보내거나_학기를_하나만_보내면_400() throws Exception {
        getMe("?today=true&academicYear=2026&semester=FIRST").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("today와 academicYear·semester는 함께 보낼 수 없습니다."));
        getMe("?academicYear=2026").andExpect(status().isBadRequest());
    }

    // ---- 직접 입력 등록 ----

    @Test
    void 직접_입력으로_등록하면_시간표를_만들고_항목_하나를_준다() throws Exception {
        add(token, """
                {"academicYear": %d, "semester": "%s", "courseName": " 스터디 ", "dayOfWeek": "WED",
                 "startTime": "18:00", "endTime": "19:30", "buildingId": %d, "room": "301호"}
                """.formatted(CURRENT.academicYear(), CURRENT.semester(), engineering.getBuildingId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timetableId").value(notNullValue()))
                .andExpect(jsonPath("$.createdItems", hasSize(1)))
                .andExpect(jsonPath("$.createdItems[0].courseId").value(nullValue()))
                .andExpect(jsonPath("$.createdItems[0].courseName").value("스터디"))
                .andExpect(jsonPath("$.createdItems[0].dayOfWeek").value("WED"))
                .andExpect(jsonPath("$.createdItems[0].startTime").value("18:00"))
                .andExpect(jsonPath("$.createdItems[0].endTime").value("19:30"))
                .andExpect(jsonPath("$.createdItems[0].building.buildingName").value("공학관"))
                .andExpect(jsonPath("$.createdItems[0].room").value("301호"));

        assertThat(timetableRepository.count()).isEqualTo(1);
    }

    @Test
    void 같은_학기에_다시_등록하면_기존_시간표에_넣는다() throws Exception {
        addManual(token, "MON", "09:00", "10:15", "자료구조").andExpect(status().isCreated());
        addManual(token, "TUE", "09:00", "10:15", "운영체제").andExpect(status().isCreated());

        assertThat(timetableRepository.count()).isEqualTo(1);
        assertThat(timetableItemRepository.count()).isEqualTo(2);
    }

    @Test
    void 직접_입력_필수값이_빠지면_400() throws Exception {
        add(token, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("과목을 고르거나 연도·학기·과목명·요일·시작·종료 시간을 모두 입력해 주세요."));
        add(token, """
                {"academicYear": 2026, "semester": "FIRST", "courseName": "스터디", "dayOfWeek": "MON", "startTime": "09:00"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void 시작_시간이_종료_시간보다_늦거나_같으면_400() throws Exception {
        addManual(token, "MON", "10:00", "10:00", "스터디").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("시작 시간은 종료 시간보다 빨라야 합니다."));
        addManual(token, "MON", "11:00", "10:00", "스터디").andExpect(status().isBadRequest());
    }

    @Test
    void 과목명이_비었거나_50자를_넘으면_400() throws Exception {
        addManual(token, "MON", "09:00", "10:00", "  ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("과목명은 1자 이상 50자 이하여야 합니다."));
        addManual(token, "MON", "09:00", "10:00", "가".repeat(51)).andExpect(status().isBadRequest());
    }

    @Test
    void 없는_건물이면_404() throws Exception {
        add(token, """
                {"academicYear": 2026, "semester": "FIRST", "courseName": "스터디", "dayOfWeek": "MON",
                 "startTime": "09:00", "endTime": "10:00", "buildingId": 999999}
                """).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("건물을 찾을 수 없습니다."));
    }

    @Test
    void 요일_값이_잘못되면_400() throws Exception {
        addManual(token, "MONDAY", "09:00", "10:00", "스터디").andExpect(status().isBadRequest());
    }

    // ---- 겹침 ----

    @Test
    void 같은_요일에_시간이_겹치면_409() throws Exception {
        addManual(token, "MON", "09:00", "10:15", "자료구조").andExpect(status().isCreated());

        addManual(token, "MON", "10:00", "11:00", "스터디").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("같은 시간에 이미 수업이 있습니다."));
        addManual(token, "MON", "08:00", "12:00", "스터디").andExpect(status().isConflict());
    }

    @Test
    void 끝나는_시각에_바로_시작하거나_다른_요일이면_겹치지_않는다() throws Exception {
        addManual(token, "MON", "09:00", "10:15", "자료구조").andExpect(status().isCreated());

        addManual(token, "MON", "10:15", "11:30", "운영체제").andExpect(status().isCreated());
        addManual(token, "TUE", "09:00", "10:15", "알고리즘").andExpect(status().isCreated());
    }

    @Test
    void 다른_사람_시간표의_수업과는_겹쳐도_된다() throws Exception {
        addManual(otherToken, "MON", "09:00", "10:15", "자료구조").andExpect(status().isCreated());

        addManual(token, "MON", "09:00", "10:15", "자료구조").andExpect(status().isCreated());
    }

    @Test
    void 같은_사용자가_겹치는_수업을_동시에_등록하면_하나만_들어간다() throws Exception {
        List<Integer> statuses = runConcurrently(
                () -> timetableService.addItem(userId, manualRequest(Weekday.MON, "09:00", "10:15")),
                () -> timetableService.addItem(userId, manualRequest(Weekday.MON, "09:30", "10:30")));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(timetableRepository.count()).isEqualTo(1);
        assertThat(timetableItemRepository.count()).isEqualTo(1);
    }

    @Test
    void 첫_수업을_동시에_등록해도_시간표는_하나만_만든다() throws Exception {
        List<Integer> statuses = runConcurrently(
                () -> timetableService.addItem(userId, manualRequest(Weekday.MON, "09:00", "10:15")),
                () -> timetableService.addItem(userId, manualRequest(Weekday.TUE, "09:00", "10:15")));

        assertThat(statuses).containsExactly(201, 201);
        assertThat(timetableRepository.count()).isEqualTo(1);
        assertThat(timetableItemRepository.count()).isEqualTo(2);
    }

    // ---- 과목 선택 등록 ----

    @Test
    void 과목을_고르면_모든_수업_시간을_항목으로_만든다() throws Exception {
        Course course = saveCourseWithSchedules(CURRENT, "01234", "01", "자료구조");

        add(token, "{\"courseId\": " + course.getCourseId() + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdItems", hasSize(2)))
                .andExpect(jsonPath("$.createdItems[0].courseId").value(course.getCourseId()))
                .andExpect(jsonPath("$.createdItems[0].subjectNo").value("01234"))
                .andExpect(jsonPath("$.createdItems[0].dvclNo").value("01"))
                .andExpect(jsonPath("$.createdItems[0].dayOfWeek").value("MON"))
                .andExpect(jsonPath("$.createdItems[0].building.buildingName").value("공학관"))
                .andExpect(jsonPath("$.createdItems[0].room").value("301호"))
                .andExpect(jsonPath("$.createdItems[1].dayOfWeek").value("WED"));

        getMe("").andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[1].courseId").value(course.getCourseId()));
    }

    @Test
    void 과목의_학기로_시간표를_정한다() throws Exception {
        AcademicTerm previous = new AcademicTerm(CURRENT.academicYear() - 1, CURRENT.semester());
        Course course = saveCourseWithSchedules(previous, "01234", "01", "자료구조");

        add(token, "{\"courseId\": " + course.getCourseId() + "}").andExpect(status().isCreated());

        getMe("").andExpect(jsonPath("$.items", hasSize(0)));
        getMe("?academicYear=" + previous.academicYear() + "&semester=" + previous.semester())
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void 같은_과목을_다시_고르면_409() throws Exception {
        Course course = saveCourseWithSchedules(CURRENT, "01234", "01", "자료구조");
        add(token, "{\"courseId\": " + course.getCourseId() + "}").andExpect(status().isCreated());

        add(token, "{\"courseId\": " + course.getCourseId() + "}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 시간표에 있는 과목입니다."));
    }

    @Test
    void 과목의_수업_시간이_기존_수업과_겹치면_아무것도_등록하지_않는다() throws Exception {
        Course course = saveCourseWithSchedules(CURRENT, "01234", "01", "자료구조");
        addManual(token, "WED", "09:30", "10:00", "스터디").andExpect(status().isCreated());

        add(token, "{\"courseId\": " + course.getCourseId() + "}").andExpect(status().isConflict());

        assertThat(timetableItemRepository.count()).isEqualTo(1);
    }

    @Test
    void 과목과_직접_입력을_함께_보내면_400() throws Exception {
        Course course = saveCourseWithSchedules(CURRENT, "01234", "01", "자료구조");

        add(token, "{\"courseId\": " + course.getCourseId() + ", \"room\": \"301호\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("과목 선택 등록과 직접 입력은 함께 보낼 수 없습니다."));
    }

    @Test
    void 없는_과목이면_404_수업_시간이_없는_과목이면_400() throws Exception {
        Course online = courseRepository.save(new Course(CURRENT, "77777", "01", "원격 강의", LocalDateTime.now()));

        add(token, "{\"courseId\": 999999}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("과목을 찾을 수 없습니다."));
        add(token, "{\"courseId\": " + online.getCourseId() + "}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("수업 시간이 정해지지 않은 과목입니다. 직접 입력으로 등록해 주세요."));
    }

    // ---- 수정 ----

    @Test
    void 보낸_필드만_수정하고_시간표_아이디와_함께_펼쳐서_준다() throws Exception {
        Long itemId = createdItemId(addManual(token, "MON", "09:00", "10:15", "자료구조"));

        update(token, itemId, "{\"startTime\": \"09:30\", \"endTime\": \"10:45\", \"room\": \"405호\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timetableId").value(notNullValue()))
                .andExpect(jsonPath("$.timetableItemId").value(itemId))
                .andExpect(jsonPath("$.courseName").value("자료구조"))
                .andExpect(jsonPath("$.dayOfWeek").value("MON"))
                .andExpect(jsonPath("$.startTime").value("09:30"))
                .andExpect(jsonPath("$.endTime").value("10:45"))
                .andExpect(jsonPath("$.room").value("405호"));
    }

    @Test
    void 강의실을_빈_문자열로_보내면_지운다() throws Exception {
        Long itemId = createdItemId(add(token, """
                {"academicYear": %d, "semester": "%s", "courseName": "스터디", "dayOfWeek": "MON",
                 "startTime": "09:00", "endTime": "10:00", "room": "301호"}
                """.formatted(CURRENT.academicYear(), CURRENT.semester())));

        update(token, itemId, "{\"room\": \"\"}").andExpect(jsonPath("$.room").value(nullValue()));
    }

    @Test
    void 자기_자신과는_겹쳐도_되고_다른_수업과_겹치면_409() throws Exception {
        Long itemId = createdItemId(addManual(token, "MON", "09:00", "10:15", "자료구조"));
        addManual(token, "MON", "11:00", "12:00", "운영체제").andExpect(status().isCreated());

        update(token, itemId, "{\"endTime\": \"10:30\"}").andExpect(status().isOk());
        update(token, itemId, "{\"endTime\": \"11:30\"}").andExpect(status().isConflict());
    }

    @Test
    void 과목에서_만든_수업의_시간을_바꿔도_과목_연결은_유지한다() throws Exception {
        Course course = saveCourseWithSchedules(CURRENT, "01234", "01", "자료구조");
        Long itemId = createdItemId(add(token, "{\"courseId\": " + course.getCourseId() + "}"));

        update(token, itemId, "{\"dayOfWeek\": \"FRI\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(course.getCourseId()))
                .andExpect(jsonPath("$.dayOfWeek").value("FRI"));
    }

    @Test
    void 수정할_항목이_없거나_시간이_거꾸로면_400() throws Exception {
        Long itemId = createdItemId(addManual(token, "MON", "09:00", "10:15", "자료구조"));

        update(token, itemId, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("수정할 항목이 없습니다."));
        update(token, itemId, "{\"startTime\": \"11:00\"}").andExpect(status().isBadRequest());
    }

    @Test
    void 다른_사람의_수업은_수정_삭제할_수_없다() throws Exception {
        Long itemId = createdItemId(addManual(otherToken, "MON", "09:00", "10:15", "자료구조"));

        update(token, itemId, "{\"room\": \"101호\"}").andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/timetables/items/" + itemId).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 없는_수업이면_404() throws Exception {
        update(token, 999999L, "{\"room\": \"101호\"}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("시간표 수업을 찾을 수 없습니다."));
        mockMvc.perform(delete("/api/v1/timetables/items/999999").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    // ---- 삭제 ----

    @Test
    void 수업을_삭제한다() throws Exception {
        Long itemId = createdItemId(addManual(token, "MON", "09:00", "10:15", "자료구조"));

        mockMvc.perform(delete("/api/v1/timetables/items/" + itemId).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        getMe("").andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.timetableId").value(notNullValue()));
    }

    // ---- 탈퇴·인증 ----

    @Test
    void 시간표가_있는_사용자도_탈퇴할_수_있다() throws Exception {
        addManual(token, "MON", "09:00", "10:15", "자료구조").andExpect(status().isCreated());
        addManual(otherToken, "MON", "09:00", "10:15", "자료구조").andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"DELETE\"}"))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(timetableRepository.count()).isEqualTo(1);
        assertThat(timetableItemRepository.count()).isEqualTo(1);
    }

    @Test
    void 인증_없이_요청하면_401() throws Exception {
        mockMvc.perform(get("/api/v1/timetables/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/timetables/items").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- 헬퍼 ----

    private Course saveCourseWithSchedules(AcademicTerm term, String subjectNo, String dvclNo, String name) {
        Course course = new Course(term, subjectNo, dvclNo, name, LocalDateTime.now());
        course.addSchedule(new CourseSchedule(course, Weekday.WED, LocalTime.of(9, 0), LocalTime.of(10, 15), engineering, "301호"));
        course.addSchedule(new CourseSchedule(course, Weekday.MON, LocalTime.of(9, 0), LocalTime.of(10, 15), engineering, "301호"));
        return courseRepository.save(course);
    }

    private TimetableItemCreateRequest manualRequest(Weekday day, String start, String end) {
        return new TimetableItemCreateRequest(null, CURRENT.academicYear(), CURRENT.semester(), "스터디", day,
                LocalTime.parse(start), LocalTime.parse(end), null, null);
    }

    // 두 작업을 최대한 같은 순간에 시작시키고 각각의 결과(201 또는 ApiException의 상태 코드)를 모은다.
    private List<Integer> runConcurrently(Runnable first, Runnable second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Runnable task : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        task.run();
                        return 201;
                    } catch (ApiException e) {
                        return e.getStatus().value();
                    }
                }));
            }
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private ResultActions getMe(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/timetables/me" + query).header("Authorization", bearer(token)));
    }

    private ResultActions addManual(String accessToken, String day, String start, String end, String name) throws Exception {
        return addManual(accessToken, CURRENT, day, start, end, name);
    }

    private ResultActions addManual(String accessToken, AcademicTerm term, String day, String start, String end,
                                    String name) throws Exception {
        return add(accessToken, """
                {"academicYear": %d, "semester": "%s", "courseName": "%s", "dayOfWeek": "%s",
                 "startTime": "%s", "endTime": "%s"}
                """.formatted(term.academicYear(), term.semester(), name, day, start, end));
    }

    private ResultActions add(String accessToken, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/timetables/items")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions update(String accessToken, Long itemId, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/timetables/items/" + itemId)
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Long createdItemId(ResultActions result) throws Exception {
        String json = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.valueOf(json.replaceAll("(?s).*?\"timetableItemId\":(\\d+).*", "$1"));
    }

    private String login(String kakaoAccessToken) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakaoAccessToken\":\"" + kakaoAccessToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}

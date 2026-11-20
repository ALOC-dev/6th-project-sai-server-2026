package com.aloc.sai.timetable;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.auth.kakao.KakaoClient;
import com.aloc.sai.auth.kakao.KakaoUserInfo;
import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.timetable.dto.TimetableItemBulkCreateRequest;
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
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 시간표 수업 일괄 등록. 빈 시간표에만 쓸 수 있고, 하나라도 잘못되면 아무것도 만들지 않는다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TimetableBulkTest {
    private static final AcademicTerm TERM = new AcademicTerm(2026, Semester.SECOND);
    private static final String TERM_JSON = "\"academicYear\": 2026, \"semester\": \"SECOND\"";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TimetableService timetableService;

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

    @MockitoBean
    private KakaoClient kakaoClient;

    private String token;
    private Long userId;
    private Building engineering;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        given(kakaoClient.getUserInfo("user-1")).willReturn(new KakaoUserInfo(1L, "지윤", null));
        token = login("user-1");
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

    @Test
    void 보낸_수업을_한_번에_만들고_요일_시작_시각순으로_준다() throws Exception {
        bulk("""
                {%s, "items": [
                  {"courseName": "운영체제", "dayOfWeek": "WED", "startTime": "13:00", "endTime": "14:15"},
                  {"courseName": "알고리즘", "dayOfWeek": "MON", "startTime": "10:30", "endTime": "11:45",
                   "buildingId": %d, "room": "  326호  "},
                  {"courseName": "  자료구조 ", "dayOfWeek": "MON", "startTime": "09:00", "endTime": "10:30"}
                ]}""".formatted(TERM_JSON, engineering.getBuildingId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdItems", hasSize(3)))
                .andExpect(jsonPath("$.createdItems[0].courseName").value("자료구조"))
                .andExpect(jsonPath("$.createdItems[0].courseId").value(nullValue()))
                .andExpect(jsonPath("$.createdItems[1].courseName").value("알고리즘"))
                .andExpect(jsonPath("$.createdItems[1].building.buildingName").value("공학관"))
                .andExpect(jsonPath("$.createdItems[1].room").value("326호"))
                .andExpect(jsonPath("$.createdItems[2].courseName").value("운영체제"));

        getMe().andExpect(jsonPath("$.items", hasSize(3)));
    }

    @Test
    void 과목을_연결해도_시간과_강의실은_보낸_값을_쓴다() throws Exception {
        Course course = saveCourse(TERM);

        bulk("""
                {%s, "items": [
                  {"courseId": %d, "courseName": "자료구조", "dayOfWeek": "TUE", "startTime": "15:00", "endTime": "16:15",
                   "room": "직접 고친 강의실"}
                ]}""".formatted(TERM_JSON, course.getCourseId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdItems[0].courseId").value(course.getCourseId()))
                .andExpect(jsonPath("$.createdItems[0].subjectNo").value("01234"))
                .andExpect(jsonPath("$.createdItems[0].dvclNo").value("01"))
                .andExpect(jsonPath("$.createdItems[0].dayOfWeek").value("TUE"))
                .andExpect(jsonPath("$.createdItems[0].startTime").value("15:00"))
                .andExpect(jsonPath("$.createdItems[0].room").value("직접 고친 강의실"))
                .andExpect(jsonPath("$.createdItems[0].building").value(nullValue()));
    }

    @Test
    void 수업을_모두_지운_시간표에는_다시_일괄_등록할_수_있다() throws Exception {
        Long itemId = timetableService.addItem(userId, manualRequest(Weekday.MON, "09:00", "10:15"))
                .createdItems().getFirst().timetableItemId();
        timetableService.deleteItem(userId, itemId);

        bulk(oneItem("MON", "09:00", "10:15")).andExpect(status().isCreated());
    }

    @Test
    void 이미_수업이_있는_시간표에는_409이고_아무것도_만들지_않는다() throws Exception {
        timetableService.addItem(userId, manualRequest(Weekday.FRI, "09:00", "10:15"));

        bulk(oneItem("MON", "09:00", "10:15"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 등록된 수업이 있는 시간표에는 일괄 등록할 수 없습니다."));
        assertThat(timetableItemRepository.count()).isEqualTo(1);
    }

    @Test
    void 다른_학기에_수업이_있어도_빈_학기에는_등록할_수_있다() throws Exception {
        timetableService.addItem(userId, new TimetableItemCreateRequest(null, 2026, Semester.FIRST, "지난 학기",
                Weekday.MON, LocalTime.of(9, 0), LocalTime.of(10, 15), null, null));

        bulk(oneItem("MON", "09:00", "10:15")).andExpect(status().isCreated());
    }

    @Test
    void 보낸_수업끼리_겹치면_400이고_아무것도_만들지_않는다() throws Exception {
        bulk("""
                {%s, "items": [
                  {"courseName": "자료구조", "dayOfWeek": "MON", "startTime": "09:00", "endTime": "10:30"},
                  {"courseName": "알고리즘", "dayOfWeek": "MON", "startTime": "10:00", "endTime": "11:15"}
                ]}""".formatted(TERM_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("보낸 수업끼리 같은 요일에 시간이 겹칩니다."));
        assertThat(timetableItemRepository.count()).isZero();
        assertThat(timetableRepository.count()).isZero();
    }

    @Test
    void 끝나는_시각에_시작하는_수업이나_다른_요일은_겹치지_않는다() throws Exception {
        bulk("""
                {%s, "items": [
                  {"courseName": "자료구조", "dayOfWeek": "MON", "startTime": "09:00", "endTime": "10:30"},
                  {"courseName": "알고리즘", "dayOfWeek": "MON", "startTime": "10:30", "endTime": "11:45"},
                  {"courseName": "운영체제", "dayOfWeek": "TUE", "startTime": "09:00", "endTime": "10:30"}
                ]}""".formatted(TERM_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdItems", hasSize(3)));
    }

    @Test
    void 연도_학기나_수업_목록이_없으면_400() throws Exception {
        bulk("""
                {"items": [{"courseName": "자료구조", "dayOfWeek": "MON", "startTime": "09:00", "endTime": "10:30"}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("연도와 학기를 입력해 주세요."));
        bulk("{%s, \"items\": []}".formatted(TERM_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("등록할 수업을 1개 이상 보내 주세요."));
        bulk("{%s}".formatted(TERM_JSON)).andExpect(status().isBadRequest());
    }

    @Test
    void 항목의_필수값_누락_시간_순서_길이_오류는_400이고_아무것도_만들지_않는다() throws Exception {
        bulk("""
                {%s, "items": [
                  {"courseName": "자료구조", "dayOfWeek": "MON", "startTime": "09:00", "endTime": "10:30"},
                  {"courseName": "알고리즘", "startTime": "13:00", "endTime": "14:15"}
                ]}""".formatted(TERM_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("수업마다 과목명·요일·시작·종료 시간을 모두 입력해 주세요."));
        bulk(oneItem("MON", "10:30", "09:00"))
                .andExpect(jsonPath("$.message").value("시작 시간은 종료 시간보다 빨라야 합니다."));
        bulk("""
                {%s, "items": [{"courseName": "   ", "dayOfWeek": "MON", "startTime": "09:00", "endTime": "10:30"}]}"""
                .formatted(TERM_JSON))
                .andExpect(status().isBadRequest());
        bulk("""
                {%s, "items": [{"courseName": "자료구조", "dayOfWeek": "MON", "startTime": "09:00", "endTime": "10:30",
                 "room": "%s"}]}""".formatted(TERM_JSON, "가".repeat(51)))
                .andExpect(status().isBadRequest());

        assertThat(timetableItemRepository.count()).isZero();
    }

    @Test
    void 다른_학기_과목을_연결하면_400() throws Exception {
        Course course = saveCourse(new AcademicTerm(2026, Semester.FIRST));

        bulk("""
                {%s, "items": [
                  {"courseId": %d, "courseName": "자료구조", "dayOfWeek": "MON", "startTime": "09:00", "endTime": "10:15"}
                ]}""".formatted(TERM_JSON, course.getCourseId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("등록하려는 학기의 과목이 아닙니다."));
    }

    @Test
    void 없는_과목이나_건물이면_404이고_아무것도_만들지_않는다() throws Exception {
        bulk("""
                {%s, "items": [
                  {"courseId": 999999, "courseName": "자료구조", "dayOfWeek": "MON", "startTime": "09:00", "endTime": "10:15"}
                ]}""".formatted(TERM_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("과목을 찾을 수 없습니다."));
        bulk("""
                {%s, "items": [
                  {"courseName": "자료구조", "dayOfWeek": "MON", "startTime": "09:00", "endTime": "10:15", "buildingId": 999999}
                ]}""".formatted(TERM_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("건물을 찾을 수 없습니다."));

        assertThat(timetableItemRepository.count()).isZero();
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(post("/api/v1/timetables/items/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oneItem("MON", "09:00", "10:15")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 같은_사용자의_일괄_등록이_동시에_와도_한_번만_성공한다() throws Exception {
        TimetableItemBulkCreateRequest request = new TimetableItemBulkCreateRequest(TERM.academicYear(), TERM.semester(),
                List.of(new TimetableItemBulkCreateRequest.BulkItem(null, "자료구조", Weekday.MON,
                        LocalTime.of(9, 0), LocalTime.of(10, 15), null, null)));

        List<Integer> statuses = runConcurrently(
                () -> timetableService.addItems(userId, request),
                () -> timetableService.addItems(userId, request));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(timetableItemRepository.count()).isEqualTo(1);
    }

    private ResultActions bulk(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/timetables/items/bulk")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions getMe() throws Exception {
        return mockMvc.perform(get("/api/v1/timetables/me?academicYear=2026&semester=SECOND")
                .header("Authorization", "Bearer " + token));
    }

    private static String oneItem(String day, String start, String end) {
        return """
                {%s, "items": [{"courseName": "자료구조", "dayOfWeek": "%s", "startTime": "%s", "endTime": "%s"}]}"""
                .formatted(TERM_JSON, day, start, end);
    }

    private static TimetableItemCreateRequest manualRequest(Weekday day, String start, String end) {
        return new TimetableItemCreateRequest(null, TERM.academicYear(), TERM.semester(), "스터디", day,
                LocalTime.parse(start), LocalTime.parse(end), null, null);
    }

    private Course saveCourse(AcademicTerm term) {
        Course course = new Course(term, "01234", "01", "자료구조", LocalDateTime.now());
        course.addSchedule(new CourseSchedule(course, Weekday.MON, LocalTime.of(9, 0), LocalTime.of(10, 15), engineering, "301호"));
        return courseRepository.save(course);
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
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(10, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            executor.shutdownNow();
        }
    }

    private String login(String kakaoAccessToken) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakaoAccessToken\":\"" + kakaoAccessToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}

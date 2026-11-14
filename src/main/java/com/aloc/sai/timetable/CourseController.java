package com.aloc.sai.timetable;

import com.aloc.sai.common.exception.ErrorResponse;
import com.aloc.sai.timetable.dto.BuildingListResponse;
import com.aloc.sai.timetable.dto.CourseSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Timetable", description = "건물 목록·과목 검색, 시간표 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CourseController {
    private static final String UNAUTHORIZED_EXAMPLE = """
            {"success": false, "message": "인증이 필요합니다.", "code": 401}""";

    private final CourseService courseService;

    @Operation(summary = "건물 목록 조회",
            description = "수업 등록 시 건물을 고를 때 씁니다. 학교 API(ApiBldg)로 채운 건물을 이름순으로 반환합니다. "
                    + "keyword를 보내면 건물명 부분 검색입니다. 위도·경도는 학교 API에 없어 따로 입력하기 전까지 null일 수 있습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE)))
    })
    @GetMapping("/buildings")
    public BuildingListResponse getBuildings(
            @Parameter(description = "건물명 부분 검색", example = "공학") @RequestParam(required = false) String keyword
    ) {
        return courseService.getBuildings(keyword);
    }

    @Operation(summary = "과목 검색",
            description = "수업 등록 화면에서 과목을 찾습니다. 과목명·교과목번호 부분 검색이며, 분반별로 학교 데이터의 수업 시간을 함께 반환합니다. "
                    + "academicYear·semester를 보내지 않으면 현재 학기(KST, 3~8월 1학기·9~2월 2학기)에서 찾습니다. "
                    + "원격·시간 미정 과목은 schedules가 빈 배열입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (결과 없으면 빈 배열)"),
            @ApiResponse(responseCode = "400", description = "keyword 누락, size 범위 초과, academicYear/semester 하나만 전달",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "검색어 누락", value = """
                                            {"success": false, "message": "검색어를 입력해 주세요.", "code": 400}"""),
                                    @ExampleObject(name = "size 범위", value = """
                                            {"success": false, "message": "size는 1 이상 50 이하여야 합니다.", "code": 400}"""),
                                    @ExampleObject(name = "학기 하나만 전달", value = """
                                            {"success": false, "message": "academicYear와 semester는 함께 보내야 합니다.", "code": 400}""")
                            })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE)))
    })
    @GetMapping("/courses")
    public CourseSearchResponse searchCourses(
            @Parameter(description = "과목명·교과목번호 부분 검색", example = "자료구조") @RequestParam(required = false) String keyword,
            @Parameter(description = "연도 (semester와 함께)", example = "2026") @RequestParam(required = false) Integer academicYear,
            @Parameter(description = "학기 (academicYear와 함께)", example = "SECOND") @RequestParam(required = false) Semester semester,
            @Parameter(description = "최대 결과 수 (기본 20, 최대 50)", example = "20") @RequestParam(required = false) Integer size
    ) {
        return courseService.searchCourses(keyword, academicYear, semester, size);
    }
}

package com.aloc.sai.timetable;

import com.aloc.sai.common.exception.ErrorResponse;
import com.aloc.sai.course.Semester;
import com.aloc.sai.security.LoginUserId;
import com.aloc.sai.timetable.dto.TimetableItemBulkCreateRequest;
import com.aloc.sai.timetable.dto.TimetableItemCreateRequest;
import com.aloc.sai.timetable.dto.TimetableItemCreateResponse;
import com.aloc.sai.timetable.dto.TimetableItemUpdateRequest;
import com.aloc.sai.timetable.dto.TimetableItemUpdateResponse;
import com.aloc.sai.timetable.dto.TimetableRecognitionResponse;
import com.aloc.sai.timetable.dto.TimetableResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Timetable", description = "건물 목록·과목 검색, 시간표 API")
@RestController
@RequestMapping("/api/v1/timetables")
@RequiredArgsConstructor
public class TimetableController {
    private static final String UNAUTHORIZED_EXAMPLE = """
            {"success": false, "message": "인증이 필요합니다.", "code": 401}""";
    private static final String OVERLAP_EXAMPLE = """
            {"success": false, "message": "같은 시간에 이미 수업이 있습니다.", "code": 409}""";
    private static final String FORBIDDEN_EXAMPLE = """
            {"success": false, "message": "내 시간표의 수업만 수정·삭제할 수 있습니다.", "code": 403}""";
    private static final String ITEM_NOT_FOUND_EXAMPLE = """
            {"success": false, "message": "시간표 수업을 찾을 수 없습니다.", "code": 404}""";

    private final TimetableService timetableService;
    private final TimetableRecognitionService timetableRecognitionService;

    @Operation(summary = "내 시간표 조회",
            description = "학기별 내 시간표와 수업 목록을 요일·시작 시각순으로 조회합니다. academicYear·semester를 보내지 않으면 현재 학기(KST)입니다. "
                    + "홈 화면은 today=true로 현재 학기의 오늘 요일 수업만 받습니다. 시간표가 없으면 timetableId·updatedAt이 null이고 items는 빈 배열입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "academicYear/semester 하나만 전달, 값 오류, today와 학기를 함께 전달",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "학기 하나만 전달", value = """
                                            {"success": false, "message": "academicYear와 semester는 함께 보내야 합니다.", "code": 400}"""),
                                    @ExampleObject(name = "today와 학기 함께 전달", value = """
                                            {"success": false, "message": "today와 academicYear·semester는 함께 보낼 수 없습니다.", "code": 400}""")
                            })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE)))
    })
    @GetMapping("/me")
    public TimetableResponse getMyTimetable(
            @LoginUserId Long userId,
            @Parameter(description = "연도 (semester와 함께)", example = "2026") @RequestParam(required = false) Integer academicYear,
            @Parameter(description = "학기 (academicYear와 함께)", example = "SECOND") @RequestParam(required = false) Semester semester,
            @Parameter(description = "오늘 요일 수업만", example = "false") @RequestParam(defaultValue = "false") boolean today
    ) {
        return timetableService.getMyTimetable(userId, academicYear, semester, today);
    }

    @Operation(summary = "시간표 이미지 인식",
            description = "multipart/form-data의 image 파트로 시간표 이미지를 올리면 수업 목록을 읽어 돌려줍니다. 저장하지 않으며, 사용자가 고친 뒤 일괄 등록 API로 저장합니다. "
                    + "읽은 수업은 academicYear·semester 학기(생략 시 현재 학기, KST)의 학교 과목과 과목명(공백 무시)과 요일·시간으로 맞춥니다. "
                    + "같은 요일에 시간이 겹치는 과목이 정확히 하나면 courseId·subjectNo·dvclNo를 채우고 과목명·시간·강의실·건물을 학교 데이터로 보정하며, 아니면 이미지에서 읽은 값을 그대로 줍니다. "
                    + "건물은 읽은 건물명과 이름이 같은 건물로 buildingId를 채우고, 건물이 정해지면 buildingName도 그 건물의 이름입니다(정해지지 않으면 읽은 값). 결과는 요일·시작 시각순입니다. "
                    + "현재는 인식 기능(AI 서버 연동)을 준비 중이라 이미지 검증을 통과하면 503을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인식 성공 (인식된 수업이 없으면 빈 배열)"),
            @ApiResponse(responseCode = "400", description = "이미지 누락, 지원하지 않는 형식(JPG·PNG·HEIC만 가능), academicYear/semester 하나만 전달",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "이미지 누락", value = """
                                            {"success": false, "message": "시간표 이미지를 첨부해 주세요.", "code": 400}"""),
                                    @ExampleObject(name = "지원하지 않는 형식", value = """
                                            {"success": false, "message": "JPG, PNG, HEIC 이미지만 인식할 수 있습니다.", "code": 400}""")
                            })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "413", description = "파일 용량 초과 (10MB)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "잘못된 요청입니다.", "code": 413}"""))),
            @ApiResponse(responseCode = "503", description = "인식 기능 준비 중",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "시간표 이미지 인식은 아직 준비 중입니다. 수업을 직접 등록해 주세요.", "code": 503}""")))
    })
    @PostMapping(value = "/recognition", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TimetableRecognitionResponse recognize(
            @LoginUserId Long userId,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @Parameter(description = "매칭할 과목의 연도 (semester와 함께)", example = "2026") @RequestParam(required = false) Integer academicYear,
            @Parameter(description = "매칭할 과목의 학기 (academicYear와 함께)", example = "SECOND") @RequestParam(required = false) Semester semester
    ) {
        return timetableRecognitionService.recognize(image, academicYear, semester);
    }

    @Operation(summary = "시간표 수업 등록",
            description = "두 방식 중 하나로 보냅니다. ① 과목 선택 등록: courseId만 보내면 학교 데이터의 모든 수업 시간을 항목으로 만들고, 학기도 과목 기준입니다. "
                    + "② 직접 입력: courseId 없이 academicYear·semester·courseName·dayOfWeek·startTime·endTime(필수), buildingId·room(선택)을 보냅니다. "
                    + "해당 학기 시간표가 없으면 자동으로 만듭니다. 같은 요일에 시간이 겹치는 수업이 있으면 409입니다(끝나는 시각에 바로 시작하는 수업은 겹치지 않음).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공 (createdItems에 만들어진 항목)"),
            @ApiResponse(responseCode = "400", description = "두 방식을 함께 보냄·둘 다 없음, 필수값 누락, 시작 ≥ 종료, 길이 초과, 수업 시간이 없는 과목, enum 값 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "두 방식을 함께 보냄", value = """
                                            {"success": false, "message": "과목 선택 등록과 직접 입력은 함께 보낼 수 없습니다.", "code": 400}"""),
                                    @ExampleObject(name = "필수값 누락", value = """
                                            {"success": false, "message": "과목을 고르거나 연도·학기·과목명·요일·시작·종료 시간을 모두 입력해 주세요.", "code": 400}"""),
                                    @ExampleObject(name = "시간 순서", value = """
                                            {"success": false, "message": "시작 시간은 종료 시간보다 빨라야 합니다.", "code": 400}"""),
                                    @ExampleObject(name = "수업 시간이 없는 과목", value = """
                                            {"success": false, "message": "수업 시간이 정해지지 않은 과목입니다. 직접 입력으로 등록해 주세요.", "code": 400}""")
                            })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "과목 또는 건물 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "과목을 찾을 수 없습니다.", "code": 404}"""))),
            @ApiResponse(responseCode = "409", description = "시간이 겹치는 수업 존재, 같은 과목이 이미 등록됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "시간 겹침", value = OVERLAP_EXAMPLE),
                                    @ExampleObject(name = "같은 과목", value = """
                                            {"success": false, "message": "이미 시간표에 있는 과목입니다.", "code": 409}""")
                            }))
    })
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public TimetableItemCreateResponse addItem(
            @LoginUserId Long userId,
            @RequestBody TimetableItemCreateRequest request
    ) {
        return timetableService.addItem(userId, request);
    }

    @Operation(summary = "시간표 수업 일괄 등록",
            description = "이미지 인식 결과를 사용자가 고친 뒤 한 번에 저장합니다. 해당 학기 시간표가 없으면 자동으로 만들고, "
                    + "이미 수업이 1개 이상 있으면 409입니다(빈 시간표에만 가능). items의 courseId(선택)는 인식에서 매칭된 과목이며, "
                    + "시간·강의실은 보낸 값을 그대로 씁니다. 보낸 수업끼리 같은 요일에 시간이 겹치면 400이고, 하나라도 잘못되면 아무것도 만들지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공 (createdItems는 요일·시작 시각순)"),
            @ApiResponse(responseCode = "400", description = "연도·학기 누락, items 비어 있음 또는 50개 초과, 항목 필수값 누락, 시작 ≥ 종료, 길이 초과, 보낸 수업끼리 시간 겹침, 다른 학기 과목, enum 값 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "items 비어 있음", value = """
                                            {"success": false, "message": "등록할 수업을 1개 이상 보내 주세요.", "code": 400}"""),
                                    @ExampleObject(name = "항목 필수값 누락", value = """
                                            {"success": false, "message": "수업마다 과목명·요일·시작·종료 시간을 모두 입력해 주세요.", "code": 400}"""),
                                    @ExampleObject(name = "보낸 수업끼리 겹침", value = """
                                            {"success": false, "message": "보낸 수업끼리 같은 요일에 시간이 겹칩니다.", "code": 400}"""),
                                    @ExampleObject(name = "다른 학기 과목", value = """
                                            {"success": false, "message": "등록하려는 학기의 과목이 아닙니다.", "code": 400}""")
                            })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "과목 또는 건물 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "건물을 찾을 수 없습니다.", "code": 404}"""))),
            @ApiResponse(responseCode = "409", description = "해당 학기 시간표에 이미 수업이 있음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "이미 등록된 수업이 있는 시간표에는 일괄 등록할 수 없습니다.", "code": 409}""")))
    })
    @PostMapping("/items/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public TimetableItemCreateResponse addItems(
            @LoginUserId Long userId,
            @RequestBody TimetableItemBulkCreateRequest request
    ) {
        return timetableService.addItems(userId, request);
    }

    @Operation(summary = "시간표 수업 수정",
            description = "보낸 필드만 수정합니다. room을 빈 문자열로 보내면 지웁니다. 학교 과목에서 만든 수업도 시간·강의실을 고칠 수 있고, 과목 연결(courseId)은 유지됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "수정할 항목 없음, 시작 ≥ 종료, 길이 초과, enum 값 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "시작 시간은 종료 시간보다 빨라야 합니다.", "code": 400}"""))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "본인 시간표의 항목이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = FORBIDDEN_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "항목 또는 건물 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ITEM_NOT_FOUND_EXAMPLE))),
            @ApiResponse(responseCode = "409", description = "시간이 겹치는 수업 존재",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = OVERLAP_EXAMPLE)))
    })
    @PatchMapping("/items/{timetableItemId}")
    public TimetableItemUpdateResponse updateItem(
            @LoginUserId Long userId,
            @PathVariable Long timetableItemId,
            @RequestBody TimetableItemUpdateRequest request
    ) {
        return timetableService.updateItem(userId, timetableItemId, request);
    }

    @Operation(summary = "시간표 수업 삭제", description = "본인 시간표의 수업 하나를 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "본인 시간표의 항목이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = FORBIDDEN_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "항목 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = ITEM_NOT_FOUND_EXAMPLE)))
    })
    @DeleteMapping("/items/{timetableItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@LoginUserId Long userId, @PathVariable Long timetableItemId) {
        timetableService.deleteItem(userId, timetableItemId);
    }
}

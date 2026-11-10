package com.aloc.sai.user;

import com.aloc.sai.common.exception.ErrorResponse;
import com.aloc.sai.security.LoginUserId;
import com.aloc.sai.user.dto.NicknameCheckResponse;
import com.aloc.sai.user.dto.UserProfileResponse;
import com.aloc.sai.user.dto.UserUpdateRequest;
import com.aloc.sai.user.dto.WithdrawRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "내 정보 조회·수정, 닉네임 중복 확인, 회원 탈퇴 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private static final String UNAUTHORIZED_EXAMPLE = """
            {"success": false, "message": "인증이 필요합니다.", "code": 401}""";
    private static final String USER_NOT_FOUND_EXAMPLE = """
            {"success": false, "message": "사용자를 찾을 수 없습니다.", "code": 404}""";

    private final UserService userService;

    @Operation(summary = "내 정보 조회",
            description = "마이페이지·앱 초기 진입 시 본인 프로필(닉네임, 프로필 이미지, 상태 메시지, 학번, 학과, 마케팅 수신 동의 여부)을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "탈퇴했거나 없는 사용자",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = USER_NOT_FOUND_EXAMPLE)))
    })
    @GetMapping("/me")
    public UserProfileResponse getMe(@LoginUserId Long userId) {
        return userService.getProfile(userId);
    }

    @Operation(summary = "닉네임 중복 확인",
            description = "초기 설정·회원정보 수정 화면에서 닉네임 사용 가능 여부를 확인합니다. "
                    + "앞뒤 공백을 제거한 뒤 1~20자(이모지 한 개를 한 글자로 셈)인지 검사하며, 본인의 현재 닉네임은 사용 가능으로 봅니다. "
                    + "응답의 nickname은 공백을 제거한 값입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "확인 성공 (available로 사용 가능 여부 반환)"),
            @ApiResponse(responseCode = "400", description = "닉네임이 비었거나 20자 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "닉네임은 1자 이상 20자 이하여야 합니다.", "code": 400}"""))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "탈퇴했거나 없는 사용자",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = USER_NOT_FOUND_EXAMPLE)))
    })
    @GetMapping("/nickname/check")
    public NicknameCheckResponse checkNickname(
            @LoginUserId Long userId,
            @Parameter(description = "확인할 닉네임", example = "공강러") @RequestParam(required = false) String nickname
    ) {
        return userService.checkNickname(userId, nickname);
    }

    @Operation(summary = "회원정보 수정 (초기 설정 포함)",
            description = "가입 직후 초기 설정(학번·학과 등록)과 마이페이지 수정을 함께 처리합니다. 보낸 필드만 수정합니다. "
                    + "초기 설정 전(profileCompleted=false)에는 학번과 학과를 함께 보내야 합니다. "
                    + "상태 메시지를 빈 문자열로 보내면 지웁니다. 마케팅 수신 동의 값이 바뀌면 동의 일시가 갱신됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공 (수정 후 프로필 반환)"),
            @ApiResponse(responseCode = "400", description = "수정할 항목 없음, 초기 설정 필수값 누락, 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "수정할 항목 없음", value = """
                                            {"success": false, "message": "수정할 항목이 없습니다.", "code": 400}"""),
                                    @ExampleObject(name = "초기 설정 필수값 누락", value = """
                                            {"success": false, "message": "초기 설정에는 학번과 학과가 모두 필요합니다.", "code": 400}"""),
                                    @ExampleObject(name = "닉네임 길이", value = """
                                            {"success": false, "message": "닉네임은 1자 이상 20자 이하여야 합니다.", "code": 400}"""),
                                    @ExampleObject(name = "학번 형식", value = """
                                            {"success": false, "message": "학번은 숫자만 입력할 수 있습니다.", "code": 400}"""),
                                    @ExampleObject(name = "학과 누락", value = """
                                            {"success": false, "message": "학과를 입력해 주세요.", "code": 400}"""),
                                    @ExampleObject(name = "상태 메시지 길이", value = """
                                            {"success": false, "message": "상태 메시지는 50자 이하여야 합니다.", "code": 400}""")
                            })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "탈퇴했거나 없는 사용자",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = USER_NOT_FOUND_EXAMPLE))),
            @ApiResponse(responseCode = "409", description = "이미 사용 중인 닉네임",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success": false, "message": "이미 사용 중인 닉네임입니다.", "code": 409}""")))
    })
    @PatchMapping("/me")
    public UserProfileResponse updateMe(
            @LoginUserId Long userId,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        return userService.updateProfile(userId, request);
    }

    @Operation(summary = "회원 탈퇴",
            description = "confirmation에 \"DELETE\"를 보내야 탈퇴합니다. 사용자와 리프레시 토큰, 친구 관계·친구 요청을 함께 삭제합니다. "
                    + "탈퇴 후 같은 카카오 계정으로 로그인하면 새로 가입됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "탈퇴 성공"),
            @ApiResponse(responseCode = "400", description = "확인 문구가 없거나 \"DELETE\"가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "확인 문구 틀림", value = """
                                            {"success": false, "message": "탈퇴 확인 문구가 올바르지 않습니다.", "code": 400}"""),
                                    @ExampleObject(name = "확인 문구 누락", value = """
                                            {"success": false, "message": "confirmation은 필수입니다.", "code": 400}""")
                            })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "이미 탈퇴했거나 없는 사용자",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = USER_NOT_FOUND_EXAMPLE)))
    })
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(
            @LoginUserId Long userId,
            @Valid @RequestBody WithdrawRequest request
    ) {
        userService.withdraw(userId, request.confirmation());
    }
}

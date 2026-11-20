package com.aloc.sai.timetable;

import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.timetable.dto.TimetableRecognitionResponse;
import com.aloc.sai.user.ProfileImageType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

// 시간표 이미지 인식. 인식 결과는 저장하지 않고, 사용자가 고친 뒤 일괄 등록 API로 저장한다.
// 인식기 호출(나중에 AI 서버)은 오래 걸릴 수 있어 트랜잭션 밖에서 하고, 학교 데이터 매칭만 읽기 트랜잭션으로 한다.
@Service
@RequiredArgsConstructor
public class TimetableRecognitionService {
    // 파일 용량(10MB)은 multipart 설정에서 막는다(413).
    private static final Set<ProfileImageType> ALLOWED_TYPES =
            Set.of(ProfileImageType.JPEG, ProfileImageType.PNG, ProfileImageType.HEIC, ProfileImageType.HEIF);

    private final TimetableImageRecognizer recognizer;
    private final CourseService courseService;

    // 학기를 보내지 않으면 현재 학기(KST) 과목과 맞춘다.
    public TimetableRecognitionResponse recognize(MultipartFile image, Integer academicYear, Semester semester) {
        AcademicTerm term = CourseService.resolveTerm(academicYear, semester);
        byte[] content = readImage(image);
        // Content-Type이나 파일명은 믿지 않고 파일 앞부분의 시그니처로 형식을 판별한다.
        ProfileImageType type = ProfileImageType.detect(content)
                .filter(ALLOWED_TYPES::contains)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "JPG, PNG, HEIC 이미지만 인식할 수 있습니다."));

        return new TimetableRecognitionResponse(
                courseService.matchRecognized(term, recognizer.recognize(content, type.contentType())));
    }

    private static byte[] readImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "시간표 이미지를 첨부해 주세요.");
        }
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "이미지 파일을 읽을 수 없습니다.", e);
        }
    }
}

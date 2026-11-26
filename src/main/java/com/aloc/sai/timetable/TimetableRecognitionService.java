package com.aloc.sai.timetable;

import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.course.AcademicTerm;
import com.aloc.sai.course.Building;
import com.aloc.sai.course.Course;
import com.aloc.sai.course.CourseSchedule;
import com.aloc.sai.course.CourseService;
import com.aloc.sai.course.Semester;
import com.aloc.sai.timetable.dto.TimetableRecognitionResponse;
import com.aloc.sai.user.ProfileImageType;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// 시간표 이미지 인식. 인식 결과는 저장하지 않고, 사용자가 고친 뒤 일괄 등록 API로 저장한다.
// 인식기 호출(나중에 AI 서버)은 오래 걸릴 수 있어 트랜잭션 밖에서 한다. 학교 과목·건물은 CourseService로 읽고(각자 읽기 트랜잭션),
// 맞추는 일은 메모리에서 한다. 과목은 수업 시간·건물까지 함께 읽으므로 트랜잭션 밖에서 접근해도 된다.
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

        return new TimetableRecognitionResponse(matchRecognized(term, recognizer.recognize(content, type.contentType())));
    }

    // 이미지에서 읽은 수업을 그 학기 학교 과목·건물과 맞춘다.
    // 과목: 이름(공백·대소문자 무시)이 같고 같은 요일에 시간이 겹치는 수업 시간이 있는 과목이 정확히 하나면 매칭하고,
    //       시간·강의실·건물은 학교 데이터 값으로 보정한다. 같은 이름의 분반이 여럿 남으면 잘못 연결하지 않도록 매칭하지 않는다.
    // 건물: 과목으로 정해지지 않으면 읽은 건물명(공백 무시)과 이름이 같은 건물이 정확히 하나일 때 매칭한다.
    //       건물이 정해지면 건물명도 그 건물의 이름으로 줘서 ID와 이름이 서로 다른 건물을 가리키지 않게 한다.
    // 과목명·요일·시간이 빠졌거나 시작 ≥ 종료인 수업은 등록할 수 없으므로 버린다.
    // 인식은 사용자당 학기에 한두 번이라, 그 학기 과목을 한 번에 읽어 메모리에서 맞춘다.
    List<TimetableRecognitionResponse.RecognizedItem> matchRecognized(AcademicTerm term, List<RecognizedClass> classes) {
        List<RecognizedClass> valid = classes.stream().filter(TimetableRecognitionService::isUsable).toList();
        if (valid.isEmpty()) {
            return List.of();
        }
        Map<String, List<Course>> coursesByName = courseService.getCoursesWithSchedules(term).stream()
                .collect(Collectors.groupingBy(course -> matchKey(course.getCourseName())));
        Map<String, List<Building>> buildingsByName = courseService.getAllBuildings().stream()
                .collect(Collectors.groupingBy(building -> matchKey(building.getBuildingName())));

        return valid.stream()
                .map(recognized -> toRecognizedItem(recognized, coursesByName, buildingsByName))
                .sorted(Comparator.comparing(TimetableRecognitionResponse.RecognizedItem::dayOfWeek)
                        .thenComparing(TimetableRecognitionResponse.RecognizedItem::startTime))
                .toList();
    }

    private static TimetableRecognitionResponse.RecognizedItem toRecognizedItem(
            RecognizedClass recognized, Map<String, List<Course>> coursesByName, Map<String, List<Building>> buildingsByName) {
        String buildingName = blankToNull(recognized.buildingName());
        Building buildingByName = buildingName == null ? null
                : single(buildingsByName.getOrDefault(matchKey(buildingName), List.of()));

        List<CourseSchedule> matches = coursesByName.getOrDefault(matchKey(recognized.courseName()), List.of()).stream()
                .flatMap(course -> course.getSchedules().stream())
                .filter(schedule -> schedule.getDayOfWeek() == recognized.dayOfWeek()
                        && schedule.getStartTime().isBefore(recognized.endTime())
                        && recognized.startTime().isBefore(schedule.getEndTime()))
                .toList();
        boolean oneCourse = !matches.isEmpty()
                && matches.stream().map(schedule -> schedule.getCourse().getCourseId()).distinct().count() == 1;

        if (!oneCourse) {
            return new TimetableRecognitionResponse.RecognizedItem(null, null, null,
                    recognized.courseName().strip(), recognized.dayOfWeek(), recognized.startTime(), recognized.endTime(),
                    buildingByName == null ? null : buildingByName.getBuildingId(),
                    buildingByName == null ? buildingName : buildingByName.getBuildingName(),
                    blankToNull(recognized.room()), recognized.confidence());
        }
        CourseSchedule schedule = matches.getFirst();
        Course course = schedule.getCourse();
        Building building = schedule.getBuilding() != null ? schedule.getBuilding() : buildingByName;
        return new TimetableRecognitionResponse.RecognizedItem(course.getCourseId(), course.getSubjectNo(), course.getDvclNo(),
                course.getCourseName(), schedule.getDayOfWeek(), schedule.getStartTime(), schedule.getEndTime(),
                building == null ? null : building.getBuildingId(),
                building == null ? buildingName : building.getBuildingName(),
                schedule.getRoom() != null ? schedule.getRoom() : blankToNull(recognized.room()),
                recognized.confidence());
    }

    private static boolean isUsable(RecognizedClass recognized) {
        return recognized != null && blankToNull(recognized.courseName()) != null && recognized.dayOfWeek() != null
                && recognized.startTime() != null && recognized.endTime() != null
                && recognized.startTime().isBefore(recognized.endTime());
    }

    // OCR은 띄어쓰기를 자주 틀리므로 공백을 모두 빼고, 영문 대소문자를 무시해 비교한다.
    private static String matchKey(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static <T> T single(List<T> candidates) {
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
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

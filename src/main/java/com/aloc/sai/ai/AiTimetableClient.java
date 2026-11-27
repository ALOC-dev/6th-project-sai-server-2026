package com.aloc.sai.ai;

import com.aloc.sai.ai.dto.AiTimetableRecognitionResponse;
import com.aloc.sai.ai.dto.AiTimetableRecognitionItem;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

// AI 시간표 인식(POST /ai/v1/timetables/recognition). 읽은 값만 받고, 학교 과목·건물 매칭은 BE timetable이 한다.
@Component
@RequiredArgsConstructor
public class AiTimetableClient {
    static final String RECOGNITION_PATH = "/ai/v1/timetables/recognition";

    private final AiClient aiClient;

    // 실패하면 AiException. 시간표가 아니면 빈 목록이다.
    public List<AiTimetableRecognitionItem> recognize(byte[] image, String contentType) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("image", new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return "timetable";
            }
        }).contentType(MediaType.parseMediaType(contentType));

        AiTimetableRecognitionResponse response = aiClient.post(
                RECOGNITION_PATH, MediaType.MULTIPART_FORM_DATA, body.build(), AiTimetableRecognitionResponse.class);
        return response == null || response.items() == null ? List.of() : response.items();
    }
}

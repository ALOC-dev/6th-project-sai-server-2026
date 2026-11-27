package com.aloc.sai.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiTimetableRecognitionResponse(List<AiTimetableRecognitionItem> items) {
}

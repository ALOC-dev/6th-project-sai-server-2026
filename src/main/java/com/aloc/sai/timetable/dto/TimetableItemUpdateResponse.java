package com.aloc.sai.timetable.dto;

import com.aloc.sai.timetable.TimetableItem;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

// 소속 시간표 아이디와 수정된 항목. 항목 필드는 timetableId와 같은 단계로 펼쳐서 내려간다.
public record TimetableItemUpdateResponse(Long timetableId, @JsonUnwrapped TimetableItemResponse item) {
    public static TimetableItemUpdateResponse from(TimetableItem item) {
        return new TimetableItemUpdateResponse(item.getTimetable().getTimetableId(), TimetableItemResponse.from(item));
    }
}

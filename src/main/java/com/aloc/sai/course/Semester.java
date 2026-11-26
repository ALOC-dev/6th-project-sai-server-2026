package com.aloc.sai.course;

// 정규 학기(1·2학기)와 계절학기(여름·겨울). termCode는 학교 시간표 API의 학기 코드다.
// 겨울 계절학기는 12월에 시작해 다음 해 1월에 끝나지만 시작한 해의 학기로 본다(학교 API와 같다).
public enum Semester {
    FIRST(10),
    SUMMER(11),
    SECOND(20),
    WINTER(21);

    private final int termCode;

    Semester(int termCode) {
        this.termCode = termCode;
    }

    public int termCode() {
        return termCode;
    }
}

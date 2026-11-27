package com.aloc.sai.ai;

// AI 서버 호출 실패. 사용자에게 무엇을 보여 줄지는 호출한 도메인이 status·code를 보고 정한다.
// status: AI가 돌려준 HTTP 상태 코드. 연결 실패·타임아웃처럼 응답을 못 받았으면 UNAVAILABLE_STATUS.
// code: AI 오류 응답의 code(예: INVALID_INPUT, FILE_TOO_LARGE). 본문이 없거나 형식이 다르면 null.
public class AiException extends RuntimeException {
    public static final int UNAVAILABLE_STATUS = 503;

    private final int status;
    private final String code;

    public AiException(int status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}

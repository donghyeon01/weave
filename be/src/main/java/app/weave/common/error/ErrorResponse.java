package app.weave.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 에러 응답 봉투 (api-contract §1.4 B′).
 * fieldErrors는 검증 실패(400) 시에만 채운다 — 빈 배열은 직렬화에서 생략.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        int status,
        String code,
        String message,
        String traceId,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String reason) {
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String traceId) {
        return new ErrorResponse(errorCode.getStatus(), errorCode.getCode(),
                message, traceId, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String traceId,
            List<FieldError> fieldErrors) {
        return new ErrorResponse(errorCode.getStatus(), errorCode.getCode(),
                message, traceId, fieldErrors);
    }
}

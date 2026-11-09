package app.weave.common.error;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 모든 예외를 api-contract §1.4 에러 봉투로 변환한다.
 * 응답에 스택트레이스·내부 메시지를 포함하지 않는다 — 추적은 traceId로만.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return respond(e.getErrorCode(), e.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors()
                .stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return respond(ErrorCode.COMMON_VALIDATION, ErrorCode.COMMON_VALIDATION.getMessage(),
                fieldErrors);
    }

    /** 쿼리·경로 변수의 타입 불일치(?cursor=abc → Long)도 해석 실패로 — 500이 아니라 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException e) {
        return respond(ErrorCode.COMMON_MALFORMED, ErrorCode.COMMON_MALFORMED.getMessage(),
                List.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformed(HttpMessageNotReadableException e) {
        return respond(ErrorCode.COMMON_MALFORMED, ErrorCode.COMMON_MALFORMED.getMessage(),
                List.of());
    }

    /** MVC 내부(컨트롤러·서비스)에서 던져진 인증/인가 예외도 봉투로 — 필터 단계는 SecurityConfig의 entry point가 담당. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
        return respond(ErrorCode.AUTH_UNAUTHORIZED, ErrorCode.AUTH_UNAUTHORIZED.getMessage(),
                List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return respond(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.getMessage(),
                List.of());
    }

    /** multipart 크기 상한은 컨트롤러 도달 전에 터진다 — 현재 유일한 업로드 경로(일기 사진) 코드로 변환. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadSize(MaxUploadSizeExceededException e) {
        return respond(ErrorCode.DIARY_PHOTO_SIZE, ErrorCode.DIARY_PHOTO_SIZE.getMessage(),
                List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        String traceId = newTraceId();
        log.error("unhandled exception traceId={}", traceId, e);
        try {
            ErrorCode code = ErrorCode.INTERNAL_ERROR;
            return ResponseEntity.status(code.getStatus())
                    .body(new ErrorResponse(code.getStatus(), code.getCode(),
                            code.getMessage(), traceId, List.of()));
        } finally {
            MDC.remove("traceId");
        }
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode errorCode, String message,
            List<ErrorResponse.FieldError> fieldErrors) {
        String traceId = newTraceId();
        try {
            return ResponseEntity.status(errorCode.getStatus())
                    .body(ErrorResponse.of(errorCode, message, traceId, fieldErrors));
        } finally {
            MDC.remove("traceId");
        }
    }

    private String newTraceId() {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", traceId);
        return traceId;
    }
}

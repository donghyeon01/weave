package app.weave.common.error;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

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

    /**
     * 지원하지 않는 메서드(PATCH 전용 경로에 PUT 등)는 catch-all의 500이 아니라 405.
     * Allow 헤더는 예외가 보유한 실제 지원 메서드 집합을 그대로 실어야 한다 (RFC 9110 §15.5.6).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException e) {
        String traceId = newTraceId();
        try {
            ErrorCode code = ErrorCode.COMMON_METHOD_NOT_ALLOWED;
            var builder = ResponseEntity.status(code.getStatus());
            String allow = e.getSupportedHttpMethods().stream()
                    .map(HttpMethod::name).collect(Collectors.joining(", "));
            if (!allow.isEmpty()) {
                builder.header(HttpHeaders.ALLOW, allow);
            }
            return builder.body(ErrorResponse.of(code, code.getMessage(), traceId, List.of()));
        } finally {
            MDC.remove("traceId");
        }
    }

    /** 필수 쿼리 파라미터 누락(/api/events/range의 start·end 등)도 500이 아니라 400. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException e) {
        return respondMissing(e.getParameterName(), "필수 파라미터가 누락되었습니다.");
    }

    /** 필수 multipart 파트 누락(일기 사진 업로드 등)도 500이 아니라 400. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(
            MissingServletRequestPartException e) {
        return respondMissing(e.getRequestPartName(), "필수 요청 파트가 누락되었습니다.");
    }

    private ResponseEntity<ErrorResponse> respondMissing(String field, String reason) {
        return respond(ErrorCode.COMMON_VALIDATION, ErrorCode.COMMON_VALIDATION.getMessage(),
                List.of(new ErrorResponse.FieldError(field, reason)));
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

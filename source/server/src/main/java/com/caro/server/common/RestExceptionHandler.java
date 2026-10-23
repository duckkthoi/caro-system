package com.caro.server.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorBody> handleApp(AppException e) {
        return ResponseEntity.status(e.code().httpStatus()).body(new ErrorBody(e.code(), e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorBody(ErrorCode.BAD_REQUEST, "Body JSON không hợp lệ"));
    }

    /** e.g. /api/matches/abc where a UUID is expected, or ?page=x */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorBody> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorBody(ErrorCode.BAD_REQUEST, "Tham số không hợp lệ: " + e.getName()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleOther(Exception e) {
        // Spring's own errors (unknown URL, wrong HTTP method, missing parameter...) already carry a status
        if (e instanceof ErrorResponse spring) {
            int status = spring.getStatusCode().value();
            ErrorBody body = status == 404
                    ? new ErrorBody(ErrorCode.NOT_FOUND, "Không tìm thấy đường dẫn")
                    : new ErrorBody(ErrorCode.BAD_REQUEST, e.getMessage());
            return ResponseEntity.status(status).body(body);
        }
        log.error("Unhandled REST error", e);
        return ResponseEntity.internalServerError().body(new ErrorBody(ErrorCode.INTERNAL_ERROR, "Lỗi hệ thống"));
    }
}

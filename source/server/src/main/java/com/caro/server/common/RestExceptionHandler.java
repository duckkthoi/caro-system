package com.caro.server.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleOther(Exception e) {
        log.error("Unhandled REST error", e);
        return ResponseEntity.internalServerError().body(new ErrorBody(ErrorCode.INTERNAL_ERROR, "Lỗi hệ thống"));
    }
}

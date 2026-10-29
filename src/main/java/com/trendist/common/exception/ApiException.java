package com.trendist.common.exception;

import org.springframework.http.HttpStatus;

/** 오류 코드와 HTTP 상태를 함께 담는 예외. 화면 문구는 클라이언트가 코드로 찾는다. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
    public static ApiException forbiddenCode(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }
    public static ApiException tooMany(String code, String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code, message);
    }
    public static ApiException unauthorized(String code) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, "인증이 필요해요");
    }
}

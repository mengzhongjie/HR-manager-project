package com.wangmian.hr_manager_project.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一 API 响应包装
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;

    private ApiResponse() {}

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public static <T> ApiResponse<T> error(String message) {
        return error(1, message);
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
}

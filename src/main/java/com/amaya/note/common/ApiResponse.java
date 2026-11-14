package com.amaya.note.common;

public record ApiResponse(Object code, String msg, Object data) {
    public static ApiResponse success(Object data) { return new ApiResponse(0, "success", data); }
    public static ApiResponse error(String code, String msg) { return new ApiResponse(code, msg, null); }
}

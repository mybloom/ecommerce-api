package com.loopers.interfaces.api;

import org.jspecify.annotations.Nullable;

public record ApiResponse<T>(Metadata meta, @Nullable T data) {
    public record Metadata(Result result, @Nullable String errorCode, @Nullable String message) {
        public enum Result {
            SUCCESS, FAIL
        }

        public static Metadata success() {
            return new Metadata(Result.SUCCESS, null, null);
        }

        public static Metadata fail(String errorCode, String errorMessage) {
            return new Metadata(Result.FAIL, errorCode, errorMessage);
        }
    }

    public static ApiResponse<Object> success() {
        return new ApiResponse<>(Metadata.success(), null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(Metadata.success(), data);
    }

    public static ApiResponse<Object> fail(String errorCode, String errorMessage) {
        return new ApiResponse<>(
            Metadata.fail(errorCode, errorMessage),
            null
        );
    }
}

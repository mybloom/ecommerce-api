package com.loopers.support.error;

import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Getter
public class CoreException extends RuntimeException {
    private final ErrorType errorType;
    @Nullable
    private final String customMessage;

    public CoreException(ErrorType errorType) {
        this(errorType, null);
    }

    public CoreException(ErrorType errorType, @Nullable String customMessage) {
        super(customMessage != null ? customMessage : errorType.getMessage());
        this.errorType = errorType;
        this.customMessage = customMessage;
    }
}

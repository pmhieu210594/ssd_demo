package com.sdd.platform.application.usecase.common;

import java.util.List;

public record PageResult<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}

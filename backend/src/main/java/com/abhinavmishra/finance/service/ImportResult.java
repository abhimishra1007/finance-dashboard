package com.abhinavmishra.finance.service;

import java.util.Map;

import com.abhinavmishra.finance.model.Category;

public record ImportResult(
        int rowsRead,
        long inserted,
        long duplicatesSkipped,
        Map<Category, Long> byCategory,
        long elapsedMillis) {
}

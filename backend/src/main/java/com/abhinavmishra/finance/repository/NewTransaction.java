package com.abhinavmishra.finance.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.abhinavmishra.finance.model.Category;
import com.abhinavmishra.finance.model.CategorySource;

/** A categorized row ready to insert. importHash makes re-importing the same file a no-op. */
public record NewTransaction(
        LocalDate date,
        String description,
        String merchant,
        BigDecimal amount,
        Category category,
        CategorySource categorySource,
        String importHash) {
}

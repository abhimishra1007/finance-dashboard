package com.abhinavmishra.finance.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Transaction(
        Long id,
        LocalDate date,
        String description,
        String merchant,
        BigDecimal amount,
        Category category,
        CategorySource categorySource) {

    public boolean isDebit() {
        return amount.signum() < 0;
    }
}

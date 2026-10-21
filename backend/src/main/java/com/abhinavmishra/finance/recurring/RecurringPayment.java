package com.abhinavmishra.finance.recurring;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.abhinavmishra.finance.model.Category;

public record RecurringPayment(
        String merchant,
        Category category,
        Cadence cadence,
        BigDecimal typicalAmount,
        int occurrences,
        LocalDate firstSeen,
        LocalDate lastSeen,
        LocalDate nextExpected,
        boolean subscription,
        boolean active) {
}

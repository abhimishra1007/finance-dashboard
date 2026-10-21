package com.abhinavmishra.finance.importer;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row from a bank CSV export, before categorization. Negative amount = money out. */
public record ParsedTransaction(LocalDate date, String description, BigDecimal amount) {
}

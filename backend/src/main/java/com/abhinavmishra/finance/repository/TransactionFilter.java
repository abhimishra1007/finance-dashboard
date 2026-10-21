package com.abhinavmishra.finance.repository;

import java.time.LocalDate;

import com.abhinavmishra.finance.model.Category;

public record TransactionFilter(LocalDate from, LocalDate to, Category category, String search) {
}

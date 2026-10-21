package com.abhinavmishra.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.abhinavmishra.finance.model.Category;
import com.abhinavmishra.finance.model.Transaction;
import com.abhinavmishra.finance.recurring.RecurringPayment;
import com.abhinavmishra.finance.recurring.RecurringPaymentDetector;
import com.abhinavmishra.finance.repository.TransactionFilter;
import com.abhinavmishra.finance.repository.TransactionRepository;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    public record CategoryTotal(Category category, String label, BigDecimal amount, long count) {
    }

    public record MonthTotal(String month, BigDecimal income, BigDecimal spending) {
    }

    public record MerchantTotal(String merchant, BigDecimal amount, long count) {
    }

    public record Summary(
            LocalDate from,
            LocalDate to,
            long transactionCount,
            BigDecimal income,
            BigDecimal spending,
            BigDecimal net,
            List<CategoryTotal> spendingByCategory,
            List<MonthTotal> monthly,
            List<MerchantTotal> topMerchants) {
    }

    public record RecurringReport(LocalDate asOf, BigDecimal monthlySubscriptionCost, List<RecurringPayment> payments) {
    }

    private final TransactionRepository transactions;
    private final RecurringPaymentDetector detector;

    public AnalyticsService(TransactionRepository transactions, RecurringPaymentDetector detector) {
        this.transactions = transactions;
        this.detector = detector;
    }

    /** Totals exclude transfers, which only move money between your own accounts. */
    public Summary summary(LocalDate from, LocalDate to) {
        List<Transaction> rows = transactions.findAll(new TransactionFilter(from, to, null, null));

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal spending = BigDecimal.ZERO;
        Map<Category, BigDecimal> byCategory = new EnumMap<>(Category.class);
        Map<Category, Long> categoryCounts = new EnumMap<>(Category.class);
        Map<YearMonth, BigDecimal[]> monthly = new TreeMap<>();
        Map<String, BigDecimal> byMerchant = new HashMap<>();
        Map<String, Long> merchantCounts = new HashMap<>();

        for (Transaction t : rows) {
            if (t.category() == Category.TRANSFER) {
                continue;
            }
            BigDecimal[] month = monthly.computeIfAbsent(YearMonth.from(t.date()),
                    m -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            if (t.isDebit()) {
                BigDecimal out = t.amount().negate();
                spending = spending.add(out);
                month[1] = month[1].add(out);
                byCategory.merge(t.category(), out, BigDecimal::add);
                categoryCounts.merge(t.category(), 1L, Long::sum);
                byMerchant.merge(t.merchant(), out, BigDecimal::add);
                merchantCounts.merge(t.merchant(), 1L, Long::sum);
            } else {
                income = income.add(t.amount());
                month[0] = month[0].add(t.amount());
            }
        }

        List<CategoryTotal> categories = byCategory.entrySet().stream()
                .map(e -> new CategoryTotal(e.getKey(), e.getKey().label(), e.getValue(), categoryCounts.get(e.getKey())))
                .sorted(Comparator.comparing(CategoryTotal::amount).reversed())
                .toList();
        List<MonthTotal> months = monthly.entrySet().stream()
                .map(e -> new MonthTotal(e.getKey().toString(), e.getValue()[0], e.getValue()[1]))
                .toList();
        List<MerchantTotal> merchants = byMerchant.entrySet().stream()
                .map(e -> new MerchantTotal(e.getKey(), e.getValue(), merchantCounts.get(e.getKey())))
                .sorted(Comparator.comparing(MerchantTotal::amount).reversed())
                .limit(10)
                .toList();

        return new Summary(from, to, rows.size(), income, spending, income.subtract(spending),
                categories, months, merchants);
    }

    /** Recurring payments across all history, judged as of the newest transaction. */
    public RecurringReport recurring() {
        List<Transaction> rows = transactions.findAll(new TransactionFilter(null, null, null, null));
        LocalDate asOf = rows.isEmpty() ? LocalDate.now() : rows.get(rows.size() - 1).date();
        List<RecurringPayment> payments = detector.detect(rows, asOf);

        BigDecimal monthlyCost = payments.stream()
                .filter(p -> p.subscription() && p.active())
                .map(p -> switch (p.cadence()) {
                    case WEEKLY -> p.typicalAmount().multiply(new BigDecimal("4.33"));
                    case BIWEEKLY -> p.typicalAmount().multiply(new BigDecimal("2.17"));
                    case MONTHLY -> p.typicalAmount();
                    case QUARTERLY -> p.typicalAmount().divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);
                    case ANNUAL -> p.typicalAmount().divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return new RecurringReport(asOf, monthlyCost, payments);
    }
}

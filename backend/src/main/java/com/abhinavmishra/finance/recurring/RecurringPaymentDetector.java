package com.abhinavmishra.finance.recurring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.abhinavmishra.finance.model.Category;
import com.abhinavmishra.finance.model.Transaction;
import org.springframework.stereotype.Component;

/**
 * Finds payments that repeat on a regular schedule.
 *
 * <p>For each merchant with at least {@link #MIN_OCCURRENCES} outgoing payments:
 * <ol>
 *   <li>Sort by date and compute the gaps between consecutive payments.</li>
 *   <li>Map each gap to a {@link Cadence} and take the most common one. At least
 *       {@link #MIN_REGULAR_FRACTION} of the gaps must agree with it.</li>
 *   <li>Check the amounts are stable: within {@link #AMOUNT_TOLERANCE} of the median.
 *       Utilities swing more, so they get {@link #UTILITY_AMOUNT_TOLERANCE}.</li>
 * </ol>
 * A series counts as a subscription when the amount is (nearly) fixed and the
 * cadence is monthly or annual, or when it was already categorized as one.
 */
@Component
public class RecurringPaymentDetector {

    static final int MIN_OCCURRENCES = 3;
    static final double MIN_REGULAR_FRACTION = 0.75;
    static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.15");
    static final BigDecimal UTILITY_AMOUNT_TOLERANCE = new BigDecimal("0.50");
    static final BigDecimal FIXED_AMOUNT_TOLERANCE = new BigDecimal("0.02");

    public List<RecurringPayment> detect(List<Transaction> transactions, LocalDate today) {
        Map<String, List<Transaction>> byMerchant = transactions.stream()
                .filter(Transaction::isDebit)
                .filter(t -> t.category() != Category.TRANSFER)
                .collect(Collectors.groupingBy(Transaction::merchant));

        List<RecurringPayment> out = new ArrayList<>();
        for (Map.Entry<String, List<Transaction>> e : byMerchant.entrySet()) {
            List<Transaction> series = new ArrayList<>(e.getValue());
            if (series.size() < MIN_OCCURRENCES) {
                continue;
            }
            series.sort(Comparator.comparing(Transaction::date));
            analyze(e.getKey(), series, today).ifPresent(out::add);
        }
        out.sort(Comparator.comparing(RecurringPayment::typicalAmount).reversed());
        return out;
    }

    private Optional<RecurringPayment> analyze(String merchant, List<Transaction> series, LocalDate today) {
        // Same-day duplicates (e.g. a charge and its retry) would create zero-day gaps.
        List<Transaction> deduped = new ArrayList<>();
        for (Transaction t : series) {
            if (deduped.isEmpty() || !deduped.get(deduped.size() - 1).date().equals(t.date())) {
                deduped.add(t);
            }
        }
        if (deduped.size() < MIN_OCCURRENCES) {
            return Optional.empty();
        }

        Map<Cadence, Integer> votes = new EnumMap<>(Cadence.class);
        int gaps = deduped.size() - 1;
        for (int i = 1; i < deduped.size(); i++) {
            long gap = ChronoUnit.DAYS.between(deduped.get(i - 1).date(), deduped.get(i).date());
            Cadence c = Cadence.forGap(gap);
            if (c != null) {
                votes.merge(c, 1, Integer::sum);
            }
        }
        if (votes.isEmpty()) {
            return Optional.empty();
        }
        Map.Entry<Cadence, Integer> best = votes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();
        if (best.getValue() < Math.ceil(gaps * MIN_REGULAR_FRACTION)) {
            return Optional.empty();
        }
        Cadence cadence = best.getKey();

        List<BigDecimal> amounts = deduped.stream().map(t -> t.amount().abs()).sorted().toList();
        BigDecimal median = amounts.get(amounts.size() / 2);
        Category category = mostCommonCategory(deduped);
        BigDecimal tolerance = category == Category.UTILITIES ? UTILITY_AMOUNT_TOLERANCE : AMOUNT_TOLERANCE;
        if (maxRelativeDeviation(amounts, median).compareTo(tolerance) > 0) {
            return Optional.empty();
        }

        boolean fixedAmount = maxRelativeDeviation(amounts, median).compareTo(FIXED_AMOUNT_TOLERANCE) <= 0;
        boolean subscription = category == Category.SUBSCRIPTIONS
                || (fixedAmount && (cadence == Cadence.MONTHLY || cadence == Cadence.ANNUAL)
                        && category != Category.HOUSING && category != Category.UTILITIES);

        LocalDate last = deduped.get(deduped.size() - 1).date();
        LocalDate next = last.plusDays(cadence.days());
        // A series that has missed two cycles has probably been cancelled.
        boolean active = !today.isAfter(last.plusDays(2L * cadence.days()));

        return Optional.of(new RecurringPayment(
                merchant, category, cadence, median.setScale(2, RoundingMode.HALF_UP), deduped.size(),
                deduped.get(0).date(), last, next, subscription, active));
    }

    private static BigDecimal maxRelativeDeviation(List<BigDecimal> amounts, BigDecimal median) {
        if (median.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal worst = BigDecimal.ZERO;
        for (BigDecimal a : amounts) {
            BigDecimal dev = a.subtract(median).abs().divide(median, 4, RoundingMode.HALF_UP);
            worst = worst.max(dev);
        }
        return worst;
    }

    private static Category mostCommonCategory(List<Transaction> series) {
        return series.stream()
                .collect(Collectors.groupingBy(Transaction::category, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(Category.OTHER);
    }
}

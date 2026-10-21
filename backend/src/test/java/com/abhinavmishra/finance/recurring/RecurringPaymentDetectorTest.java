package com.abhinavmishra.finance.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.abhinavmishra.finance.model.Category;
import com.abhinavmishra.finance.model.CategorySource;
import com.abhinavmishra.finance.model.Transaction;
import org.junit.jupiter.api.Test;

class RecurringPaymentDetectorTest {

    private final RecurringPaymentDetector detector = new RecurringPaymentDetector();
    private long nextId = 1;

    private Transaction tx(String merchant, LocalDate date, String amount, Category category) {
        return new Transaction(nextId++, date, merchant, merchant, new BigDecimal(amount), category, CategorySource.RULE);
    }

    private List<Transaction> monthly(String merchant, String amount, Category category, int months) {
        List<Transaction> out = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            out.add(tx(merchant, LocalDate.of(2026, 1, 15).plusMonths(i), amount, category));
        }
        return out;
    }

    @Test
    void detectsMonthlySubscription() {
        List<RecurringPayment> found = detector.detect(
                monthly("NETFLIX", "-15.49", Category.SUBSCRIPTIONS, 6), LocalDate.of(2026, 6, 20));
        assertThat(found).hasSize(1);
        RecurringPayment p = found.get(0);
        assertThat(p.cadence()).isEqualTo(Cadence.MONTHLY);
        assertThat(p.subscription()).isTrue();
        assertThat(p.active()).isTrue();
        assertThat(p.typicalAmount()).isEqualByComparingTo("15.49");
        assertThat(p.nextExpected()).isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @Test
    void fixedAmountMonthlyChargeIsSubscriptionEvenIfUncategorized() {
        List<RecurringPayment> found = detector.detect(
                monthly("GYM PLACE", "-24.99", Category.OTHER, 4), LocalDate.of(2026, 4, 20));
        assertThat(found).singleElement().satisfies(p -> assertThat(p.subscription()).isTrue());
    }

    @Test
    void utilityWithVaryingBillIsRecurringButNotSubscription() {
        List<Transaction> bills = new ArrayList<>();
        String[] amounts = {"-48.20", "-61.75", "-55.10", "-44.90", "-70.05"};
        for (int i = 0; i < amounts.length; i++) {
            bills.add(tx("ROCKY MOUNTAIN POWER", LocalDate.of(2026, 1, 3).plusMonths(i), amounts[i], Category.UTILITIES));
        }
        List<RecurringPayment> found = detector.detect(bills, LocalDate.of(2026, 5, 10));
        assertThat(found).singleElement().satisfies(p -> {
            assertThat(p.cadence()).isEqualTo(Cadence.MONTHLY);
            assertThat(p.subscription()).isFalse();
        });
    }

    @Test
    void detectsWeeklyCadence() {
        List<Transaction> rows = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            rows.add(tx("MEAL KIT CO", LocalDate.of(2026, 3, 2).plusWeeks(i), "-59.99", Category.OTHER));
        }
        assertThat(detector.detect(rows, LocalDate.of(2026, 4, 10)))
                .singleElement().extracting(RecurringPayment::cadence).isEqualTo(Cadence.WEEKLY);
    }

    @Test
    void ignoresIrregularSpending() {
        List<Transaction> rows = List.of(
                tx("SMITHS", LocalDate.of(2026, 1, 2), "-40.00", Category.GROCERIES),
                tx("SMITHS", LocalDate.of(2026, 1, 5), "-82.00", Category.GROCERIES),
                tx("SMITHS", LocalDate.of(2026, 1, 19), "-15.00", Category.GROCERIES),
                tx("SMITHS", LocalDate.of(2026, 1, 22), "-63.00", Category.GROCERIES));
        assertThat(detector.detect(rows, LocalDate.of(2026, 2, 1))).isEmpty();
    }

    @Test
    void ignoresRegularScheduleWithUnstableAmounts() {
        List<Transaction> rows = new ArrayList<>();
        String[] amounts = {"-10.00", "-45.00", "-12.00", "-80.00"};
        for (int i = 0; i < amounts.length; i++) {
            rows.add(tx("SHOP", LocalDate.of(2026, 1, 1).plusMonths(i), amounts[i], Category.SHOPPING));
        }
        assertThat(detector.detect(rows, LocalDate.of(2026, 5, 1))).isEmpty();
    }

    @Test
    void needsAtLeastThreePayments() {
        assertThat(detector.detect(monthly("NETFLIX", "-15.49", Category.SUBSCRIPTIONS, 2),
                LocalDate.of(2026, 3, 1))).isEmpty();
    }

    @Test
    void marksSeriesInactiveAfterTwoMissedCycles() {
        List<RecurringPayment> found = detector.detect(
                monthly("HULU", "-7.99", Category.SUBSCRIPTIONS, 4), LocalDate.of(2026, 7, 1));
        assertThat(found).singleElement().satisfies(p -> assertThat(p.active()).isFalse());
    }

    @Test
    void ignoresIncomeAndTransfers() {
        List<Transaction> rows = new ArrayList<>(monthly("ACME PAYROLL", "2000.00", Category.INCOME, 4));
        rows.addAll(monthly("SAVINGS", "-100.00", Category.TRANSFER, 4));
        assertThat(detector.detect(rows, LocalDate.of(2026, 5, 1))).isEmpty();
    }
}

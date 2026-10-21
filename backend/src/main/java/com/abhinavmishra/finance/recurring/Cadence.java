package com.abhinavmishra.finance.recurring;

/** Billing cycles the detector recognizes, with the gap (in days) each one allows. */
public enum Cadence {
    WEEKLY(7, 1),
    BIWEEKLY(14, 2),
    MONTHLY(30, 3),   // 28-33 days covers short and long months
    QUARTERLY(91, 6),
    ANNUAL(365, 7);

    private final int days;
    private final int toleranceDays;

    Cadence(int days, int toleranceDays) {
        this.days = days;
        this.toleranceDays = toleranceDays;
    }

    public int days() {
        return days;
    }

    public boolean matches(long gapDays) {
        return Math.abs(gapDays - days) <= toleranceDays;
    }

    /** The cadence whose window contains the gap, or null. */
    public static Cadence forGap(long gapDays) {
        for (Cadence c : values()) {
            if (c.matches(gapDays)) {
                return c;
            }
        }
        return null;
    }
}

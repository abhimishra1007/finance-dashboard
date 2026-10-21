package com.abhinavmishra.finance.model;

/** How a transaction got its category. */
public enum CategorySource {
    /** Matched a keyword rule. */
    RULE,
    /** Merchant was re-categorized by the user before, so the import reused that choice. */
    MERCHANT_OVERRIDE,
    /** Set by the user on this transaction. */
    MANUAL
}

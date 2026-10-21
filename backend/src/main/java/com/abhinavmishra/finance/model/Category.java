package com.abhinavmishra.finance.model;

public enum Category {
    INCOME("Income"),
    TRANSFER("Transfer"),
    HOUSING("Housing"),
    UTILITIES("Utilities"),
    GROCERIES("Groceries"),
    DINING("Dining"),
    TRANSPORTATION("Transportation"),
    SHOPPING("Shopping"),
    SUBSCRIPTIONS("Subscriptions"),
    ENTERTAINMENT("Entertainment"),
    HEALTH("Health"),
    EDUCATION("Education"),
    TRAVEL("Travel"),
    FEES("Fees"),
    OTHER("Other");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

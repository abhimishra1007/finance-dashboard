CREATE TABLE transactions (
    id               BIGSERIAL PRIMARY KEY,
    posted_date      DATE           NOT NULL,
    description      VARCHAR(512)   NOT NULL,
    merchant         VARCHAR(255)   NOT NULL,
    amount           NUMERIC(12, 2) NOT NULL,  -- negative = money out
    category         VARCHAR(32)    NOT NULL,
    category_source  VARCHAR(24)    NOT NULL,  -- RULE, MERCHANT_OVERRIDE, MANUAL
    import_hash      CHAR(64)       NOT NULL UNIQUE,
    imported_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transactions_date ON transactions (posted_date);
CREATE INDEX idx_transactions_merchant ON transactions (merchant);
CREATE INDEX idx_transactions_category ON transactions (category);

-- When a user re-categorizes a transaction, the merchant is remembered so
-- future imports from that merchant get the same category.
CREATE TABLE merchant_overrides (
    merchant    VARCHAR(255) PRIMARY KEY,
    category    VARCHAR(32)  NOT NULL,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

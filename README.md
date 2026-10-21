# Personal Finance Intelligence Dashboard

A full-stack web app that imports bank transactions from CSV, categorizes them,
detects recurring payments and subscriptions, and shows where the money goes.

- **Backend**: Java 17, Spring Boot 3 REST API, PostgreSQL (schema managed by Flyway)
- **Frontend**: React 18 + Vite, charts with Recharts

## Features

- **CSV import** that adapts to the bank's format: finds the date, description, and
  amount columns from the header, and handles signed amounts or separate debit/credit
  columns, `$1,234.56` and `(12.34)` style numbers, and several date formats.
  Re-importing a file is safe: each row gets a content hash, and rows already stored are skipped.
  Rows are inserted with JDBC batch inserts.
- **Merchant normalization**: strips the noise banks add (`POS PURCHASE`, `SQ *`, store
  numbers, masked card numbers, reference IDs, city/state) so
  `POS PURCHASE SQ *BLUE COPPER 0412 SALT LAKE CTY UT` becomes `BLUE COPPER`.
- **Categorization**: ordered whole-word keyword rules in
  [`category-rules.json`](backend/src/main/resources/category-rules.json), with a sign-based
  fallback. When you change a transaction's category you can apply it to the whole
  merchant; that choice is stored and wins over the rules for every future import.
- **Recurring-payment detection**: groups outgoing payments by merchant, classifies the
  gaps between payments as weekly / biweekly / monthly / quarterly / annual, requires
  most gaps to agree and the amounts to be stable, then flags fixed-amount monthly or
  annual series as subscriptions. It predicts the next charge and marks a series as
  stopped after two missed cycles. Details in
  [`RecurringPaymentDetector`](backend/src/main/java/com/abhinavmishra/finance/recurring/RecurringPaymentDetector.java).
- **Dashboard**: income, spending, and net for any date range (transfers between your
  own accounts are excluded), monthly income vs. spending, spending by category, top
  merchants, recurring payments, and estimated monthly subscription cost.
- **Accuracy check**: `EvaluateCategorizer` scores the categorizer against a hand-labeled
  CSV and prints accuracy, per-category precision/recall, and every miss.

## Running it

Requirements: Java 17+, Maven, Node 18+, and PostgreSQL (or Docker).

```sh
# 1. Database
docker compose up -d            # or point DB_URL / DB_USER / DB_PASSWORD at your own PostgreSQL

# 2. API on http://localhost:8080 (Flyway creates the tables on first start)
cd backend
mvn spring-boot:run

# 3. Front end on http://localhost:5173 (proxies /api to the backend)
cd ../frontend
npm install
npm run dev
```

No bank export handy? Generate a made-up one and import it on the Import tab:

```sh
python3 scripts/generate_sample_csv.py --months 15 > sample.csv
```

## Tests

```sh
cd backend
mvn test
```

Unit tests cover the CSV parser, merchant normalizer, categorizer rules, and recurring
detector. `ApiIntegrationTest` runs the whole API (import, idempotent re-import,
summary, recurring detection, merchant re-categorization, error responses) against an
in-memory H2 database in PostgreSQL mode.

To measure categorization accuracy on your own labeled data
(columns `description,amount,expected_category`):

```sh
mvn -q compile exec:java \
  -Dexec.mainClass=com.abhinavmishra.finance.tools.EvaluateCategorizer \
  -Dexec.args="path/to/labeled.csv"
```

## API

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/transactions/import` | multipart `file`: bank CSV. Returns rows read, inserted, skipped, and per-category counts |
| GET | `/api/transactions?from&to&category&q&page&size` | paged, filtered list |
| PATCH | `/api/transactions/{id}` | `{"category": "DINING", "applyToMerchant": true}` |
| DELETE | `/api/transactions` | remove all transactions |
| GET | `/api/categories` | category list |
| GET | `/api/analytics/summary?from&to` | totals, monthly series, by category, top merchants |
| GET | `/api/analytics/recurring` | detected recurring payments and monthly subscription cost |

Errors come back as RFC 7807 problem details (`422` for an unreadable CSV, `404` for an unknown id).

## Layout

```
backend/src/main/java/com/abhinavmishra/finance/
  importer/     CSV parsing
  categorize/   merchant normalization and categorization rules
  recurring/    recurring-payment and subscription detection
  repository/   JdbcTemplate data access
  service/      import, analytics, re-categorization
  web/          REST controllers, error handling, CORS
  tools/        categorization accuracy evaluator
backend/src/main/resources/
  db/migration/ Flyway SQL
  category-rules.json
frontend/src/
  components/   Dashboard, Transactions, ImportPanel, RecurringTable
  api.js        API client
scripts/generate_sample_csv.py   synthetic demo data
```

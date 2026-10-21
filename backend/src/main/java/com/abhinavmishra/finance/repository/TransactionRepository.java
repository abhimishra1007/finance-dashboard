package com.abhinavmishra.finance.repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.abhinavmishra.finance.model.Category;
import com.abhinavmishra.finance.model.CategorySource;
import com.abhinavmishra.finance.model.Transaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TransactionRepository {

    private static final String COLUMNS = "id, posted_date, description, merchant, amount, category, category_source";

    private final JdbcTemplate jdbc;

    public TransactionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts in JDBC batches; rows whose import_hash already exists are skipped. */
    public void insertAll(List<NewTransaction> rows) {
        jdbc.batchUpdate(
                // Portable "insert if absent" (works on PostgreSQL and the H2 test database).
                "INSERT INTO transactions (posted_date, description, merchant, amount, category, category_source, import_hash) "
                        + "SELECT ?, ?, ?, ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM transactions WHERE import_hash = ?)",
                rows, 500, (ps, t) -> {
                    ps.setDate(1, Date.valueOf(t.date()));
                    ps.setString(2, t.description());
                    ps.setString(3, t.merchant());
                    ps.setBigDecimal(4, t.amount());
                    ps.setString(5, t.category().name());
                    ps.setString(6, t.categorySource().name());
                    ps.setString(7, t.importHash());
                    ps.setString(8, t.importHash());
                });
    }

    public List<Transaction> find(TransactionFilter f, int limit, int offset) {
        List<Object> args = new ArrayList<>();
        String where = where(f, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM transactions" + where + " ORDER BY posted_date DESC, id DESC LIMIT ? OFFSET ?",
                TransactionRepository::map, args.toArray());
    }

    public long count(TransactionFilter f) {
        List<Object> args = new ArrayList<>();
        String where = where(f, args);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM transactions" + where, Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public List<Transaction> findAll(TransactionFilter f) {
        List<Object> args = new ArrayList<>();
        String where = where(f, args);
        return jdbc.query("SELECT " + COLUMNS + " FROM transactions" + where + " ORDER BY posted_date",
                TransactionRepository::map, args.toArray());
    }

    public Optional<Transaction> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM transactions WHERE id = ?", TransactionRepository::map, id)
                .stream().findFirst();
    }

    public void updateCategory(long id, Category category, CategorySource source) {
        jdbc.update("UPDATE transactions SET category = ?, category_source = ? WHERE id = ?",
                category.name(), source.name(), id);
    }

    /** Re-categorizes a merchant's other transactions, leaving ones the user set by hand alone. */
    public int updateMerchantCategory(String merchant, Category category) {
        return jdbc.update(
                "UPDATE transactions SET category = ?, category_source = ? WHERE merchant = ? AND category_source <> ?",
                category.name(), CategorySource.MERCHANT_OVERRIDE.name(), merchant, CategorySource.MANUAL.name());
    }

    public int deleteAll() {
        return jdbc.update("DELETE FROM transactions");
    }

    private static String where(TransactionFilter f, List<Object> args) {
        List<String> clauses = new ArrayList<>();
        if (f.from() != null) {
            clauses.add("posted_date >= ?");
            args.add(Date.valueOf(f.from()));
        }
        if (f.to() != null) {
            clauses.add("posted_date <= ?");
            args.add(Date.valueOf(f.to()));
        }
        if (f.category() != null) {
            clauses.add("category = ?");
            args.add(f.category().name());
        }
        if (f.search() != null && !f.search().isBlank()) {
            clauses.add("(UPPER(description) LIKE ? OR merchant LIKE ?)");
            String like = "%" + f.search().trim().toUpperCase() + "%";
            args.add(like);
            args.add(like);
        }
        return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
    }

    private static Transaction map(ResultSet rs, int row) throws SQLException {
        return new Transaction(
                rs.getLong("id"),
                rs.getDate("posted_date").toLocalDate(),
                rs.getString("description"),
                rs.getString("merchant"),
                rs.getBigDecimal("amount"),
                Category.valueOf(rs.getString("category")),
                CategorySource.valueOf(rs.getString("category_source")));
    }
}

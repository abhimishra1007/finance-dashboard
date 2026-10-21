package com.abhinavmishra.finance.repository;

import java.util.HashMap;
import java.util.Map;

import com.abhinavmishra.finance.model.Category;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MerchantOverrideRepository {

    private final JdbcTemplate jdbc;

    public MerchantOverrideRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Category> findAll() {
        Map<String, Category> out = new HashMap<>();
        jdbc.query("SELECT merchant, category FROM merchant_overrides",
                rs -> {
                    out.put(rs.getString("merchant"), Category.valueOf(rs.getString("category")));
                });
        return out;
    }

    public void save(String merchant, Category category) {
        int updated = jdbc.update(
                "UPDATE merchant_overrides SET category = ?, updated_at = CURRENT_TIMESTAMP WHERE merchant = ?",
                category.name(), merchant);
        if (updated == 0) {
            jdbc.update("INSERT INTO merchant_overrides (merchant, category) VALUES (?, ?)", merchant, category.name());
        }
    }
}

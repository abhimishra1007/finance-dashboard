package com.abhinavmishra.finance.categorize;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.abhinavmishra.finance.model.Category;
import com.abhinavmishra.finance.model.CategorySource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Assigns a category to a transaction in three steps:
 * <ol>
 *   <li>If the user has re-categorized this merchant before, reuse their choice.</li>
 *   <li>Otherwise try the keyword rules in {@code category-rules.json}, in order.</li>
 *   <li>Otherwise fall back on the sign: money in is {@link Category#INCOME}, money out is {@link Category#OTHER}.</li>
 * </ol>
 * Rules are matched on whole words against both the normalized merchant and
 * the raw description, since some useful words (e.g. "PAYROLL") get stripped
 * during normalization.
 */
@Component
public class TransactionCategorizer {

    public record Result(Category category, CategorySource source) {
    }

    private record Rule(Category category, Pattern pattern) {
    }

    private final List<Rule> rules;

    public TransactionCategorizer(ObjectMapper mapper) {
        this.rules = loadRules(mapper);
    }

    public Result categorize(String merchant, String description, BigDecimal amount, Map<String, Category> merchantOverrides) {
        Category override = merchantOverrides.get(merchant);
        if (override != null) {
            return new Result(override, CategorySource.MERCHANT_OVERRIDE);
        }

        String haystack = " " + merchant + " | " + description.toUpperCase(Locale.ROOT) + " ";
        for (Rule rule : rules) {
            if (rule.pattern().matcher(haystack).find()) {
                // A positive amount that matches a store is a refund and keeps the store's category.
                return new Result(rule.category(), CategorySource.RULE);
            }
        }
        return new Result(amount.signum() > 0 ? Category.INCOME : Category.OTHER, CategorySource.RULE);
    }

    public Result categorize(String merchant, String description, BigDecimal amount) {
        return categorize(merchant, description, amount, Map.of());
    }

    private static List<Rule> loadRules(ObjectMapper mapper) {
        try (InputStream in = TransactionCategorizer.class.getResourceAsStream("/category-rules.json")) {
            if (in == null) {
                throw new IllegalStateException("category-rules.json not found on classpath");
            }
            JsonNode root = mapper.readTree(in);
            List<Rule> out = new ArrayList<>();
            for (JsonNode node : root.get("rules")) {
                Category category = Category.valueOf(node.get("category").asText());
                List<String> alternatives = new ArrayList<>();
                for (JsonNode kw : node.get("keywords")) {
                    alternatives.add(Pattern.quote(kw.asText().toUpperCase(Locale.ROOT)));
                }
                // Whole-word match: keyword must not be glued to other letters or digits.
                Pattern p = Pattern.compile("(?<![A-Z0-9])(?:" + String.join("|", alternatives) + ")(?![A-Z0-9])");
                out.add(new Rule(category, p));
            }
            return List.copyOf(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

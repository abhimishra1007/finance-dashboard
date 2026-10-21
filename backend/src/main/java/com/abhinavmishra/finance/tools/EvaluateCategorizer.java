package com.abhinavmishra.finance.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.abhinavmishra.finance.categorize.MerchantNormalizer;
import com.abhinavmishra.finance.categorize.TransactionCategorizer;
import com.abhinavmishra.finance.model.Category;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Measures categorization accuracy against a hand-labeled CSV with the columns
 * {@code description,amount,expected_category}. Prints overall accuracy,
 * per-category precision and recall, and every misclassified row.
 *
 * <pre>
 * mvn -q compile exec:java -Dexec.mainClass=com.abhinavmishra.finance.tools.EvaluateCategorizer \
 *     -Dexec.args="path/to/labeled.csv"
 * </pre>
 */
public final class EvaluateCategorizer {

    private EvaluateCategorizer() {
    }

    private record Labeled(String description, BigDecimal amount, Category expected) {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: EvaluateCategorizer <labeled.csv>");
            System.exit(2);
        }
        List<Labeled> rows = read(Path.of(args[0]));
        MerchantNormalizer normalizer = new MerchantNormalizer();
        TransactionCategorizer categorizer = new TransactionCategorizer(new ObjectMapper());

        int correct = 0;
        Map<Category, int[]> stats = new EnumMap<>(Category.class); // {true positives, predicted, actual}
        List<String> misses = new ArrayList<>();

        for (Labeled row : rows) {
            Category predicted = categorizer
                    .categorize(normalizer.normalize(row.description()), row.description(), row.amount())
                    .category();
            stats.computeIfAbsent(predicted, c -> new int[3])[1]++;
            stats.computeIfAbsent(row.expected(), c -> new int[3])[2]++;
            if (predicted == row.expected()) {
                correct++;
                stats.get(predicted)[0]++;
            } else {
                misses.add(String.format("  %-45s %10s  expected %-14s got %s",
                        truncate(row.description(), 45), row.amount(), row.expected(), predicted));
            }
        }

        System.out.printf(Locale.ROOT, "Accuracy: %d/%d = %.1f%%%n%n", correct, rows.size(),
                rows.isEmpty() ? 0.0 : 100.0 * correct / rows.size());
        System.out.printf("%-15s %9s %9s %8s%n", "category", "precision", "recall", "support");
        for (Map.Entry<Category, int[]> e : stats.entrySet()) {
            int[] s = e.getValue();
            System.out.printf(Locale.ROOT, "%-15s %8.1f%% %8.1f%% %8d%n", e.getKey(),
                    s[1] == 0 ? 0.0 : 100.0 * s[0] / s[1],
                    s[2] == 0 ? 0.0 : 100.0 * s[0] / s[2],
                    s[2]);
        }
        if (!misses.isEmpty()) {
            System.out.println("\nMisclassified:");
            misses.forEach(System.out::println);
        }
    }

    private static List<Labeled> read(Path path) throws IOException {
        List<Labeled> out = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line = r.readLine(); // header
            int lineNo = 1;
            while ((line = r.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                // description may contain commas, so split from the right
                int c2 = line.lastIndexOf(',');
                int c1 = line.lastIndexOf(',', c2 - 1);
                if (c1 < 0) {
                    throw new IllegalArgumentException("line " + lineNo + ": expected description,amount,expected_category");
                }
                String description = line.substring(0, c1).replaceAll("^\"|\"$", "");
                out.add(new Labeled(description,
                        new BigDecimal(line.substring(c1 + 1, c2).trim()),
                        Category.valueOf(line.substring(c2 + 1).trim().toUpperCase(Locale.ROOT))));
            }
        }
        return out;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}

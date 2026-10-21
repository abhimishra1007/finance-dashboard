package com.abhinavmishra.finance.service;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import com.abhinavmishra.finance.categorize.MerchantNormalizer;
import com.abhinavmishra.finance.categorize.TransactionCategorizer;
import com.abhinavmishra.finance.importer.CsvTransactionParser;
import com.abhinavmishra.finance.importer.ParsedTransaction;
import com.abhinavmishra.finance.model.Category;
import com.abhinavmishra.finance.repository.MerchantOverrideRepository;
import com.abhinavmishra.finance.repository.NewTransaction;
import com.abhinavmishra.finance.repository.TransactionFilter;
import com.abhinavmishra.finance.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Parses a bank CSV, normalizes merchants, categorizes each row, and batch-inserts. */
@Service
public class ImportService {

    private static final TransactionFilter ALL = new TransactionFilter(null, null, null, null);

    private final CsvTransactionParser parser;
    private final MerchantNormalizer normalizer;
    private final TransactionCategorizer categorizer;
    private final TransactionRepository transactions;
    private final MerchantOverrideRepository overrides;

    public ImportService(CsvTransactionParser parser, MerchantNormalizer normalizer, TransactionCategorizer categorizer,
                         TransactionRepository transactions, MerchantOverrideRepository overrides) {
        this.parser = parser;
        this.normalizer = normalizer;
        this.categorizer = categorizer;
        this.transactions = transactions;
        this.overrides = overrides;
    }

    @Transactional
    public ImportResult importCsv(Reader csv) throws IOException {
        long start = System.nanoTime();
        List<ParsedTransaction> parsed = parser.parse(csv);
        Map<String, Category> merchantOverrides = overrides.findAll();

        // Identical rows can legitimately appear twice (two coffees on the same day),
        // so the hash includes how many times this exact row has been seen in the file.
        Map<String, Integer> seen = new HashMap<>();
        Map<Category, Long> byCategory = new EnumMap<>(Category.class);
        List<NewTransaction> rows = new ArrayList<>(parsed.size());

        for (ParsedTransaction p : parsed) {
            String key = p.date() + "|" + p.description() + "|" + p.amount().stripTrailingZeros().toPlainString();
            int occurrence = seen.merge(key, 1, Integer::sum);
            String merchant = normalizer.normalize(p.description());
            TransactionCategorizer.Result r = categorizer.categorize(merchant, p.description(), p.amount(), merchantOverrides);
            byCategory.merge(r.category(), 1L, Long::sum);
            rows.add(new NewTransaction(p.date(), p.description(), merchant, p.amount(), r.category(), r.source(),
                    sha256(key + "|" + occurrence)));
        }

        long before = transactions.count(ALL);
        transactions.insertAll(rows);
        long inserted = transactions.count(ALL) - before;

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return new ImportResult(parsed.size(), inserted, parsed.size() - inserted, byCategory, elapsedMs);
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

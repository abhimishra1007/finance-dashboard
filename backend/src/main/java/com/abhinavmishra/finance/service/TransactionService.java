package com.abhinavmishra.finance.service;

import java.util.List;
import java.util.NoSuchElementException;

import com.abhinavmishra.finance.model.Category;
import com.abhinavmishra.finance.model.CategorySource;
import com.abhinavmishra.finance.model.Transaction;
import com.abhinavmishra.finance.repository.MerchantOverrideRepository;
import com.abhinavmishra.finance.repository.TransactionFilter;
import com.abhinavmishra.finance.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    public record Page(List<Transaction> items, long total, int page, int size) {
    }

    public record RecategorizeResult(Transaction transaction, int otherTransactionsUpdated) {
    }

    private final TransactionRepository transactions;
    private final MerchantOverrideRepository overrides;

    public TransactionService(TransactionRepository transactions, MerchantOverrideRepository overrides) {
        this.transactions = transactions;
        this.overrides = overrides;
    }

    public Page list(TransactionFilter filter, int page, int size) {
        return new Page(transactions.find(filter, size, page * size), transactions.count(filter), page, size);
    }

    /**
     * Sets a transaction's category. With applyToMerchant, the choice is remembered for
     * the merchant: its other transactions and all future imports get the same category.
     */
    @Transactional
    public RecategorizeResult recategorize(long id, Category category, boolean applyToMerchant) {
        Transaction t = transactions.findById(id)
                .orElseThrow(() -> new NoSuchElementException("transaction " + id + " not found"));
        int others = 0;
        if (applyToMerchant) {
            overrides.save(t.merchant(), category);
            others = transactions.updateMerchantCategory(t.merchant(), category);
        }
        transactions.updateCategory(id, category, CategorySource.MANUAL);
        Transaction updated = transactions.findById(id).orElseThrow();
        // updateMerchantCategory also touched this transaction unless it was already set by hand.
        if (others > 0 && t.categorySource() != CategorySource.MANUAL) {
            others--;
        }
        return new RecategorizeResult(updated, others);
    }

    public int deleteAll() {
        return transactions.deleteAll();
    }
}

package com.abhinavmishra.finance.web;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.abhinavmishra.finance.model.Category;
import com.abhinavmishra.finance.repository.TransactionFilter;
import com.abhinavmishra.finance.service.ImportResult;
import com.abhinavmishra.finance.service.ImportService;
import com.abhinavmishra.finance.service.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@Validated
public class TransactionController {

    public record CategoryUpdate(@NotNull Category category, boolean applyToMerchant) {
    }

    public record CategoryOption(Category value, String label) {
    }

    private final ImportService importService;
    private final TransactionService transactionService;

    public TransactionController(ImportService importService, TransactionService transactionService) {
        this.importService = importService;
        this.transactionService = transactionService;
    }

    @PostMapping(path = "/transactions/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResult importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("uploaded file is empty");
        }
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            return importService.importCsv(reader);
        }
    }

    @GetMapping("/transactions")
    public TransactionService.Page list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        return transactionService.list(new TransactionFilter(from, to, category, q), page, size);
    }

    @PatchMapping("/transactions/{id}")
    public TransactionService.RecategorizeResult recategorize(@PathVariable long id,
                                                              @Valid @RequestBody CategoryUpdate update) {
        return transactionService.recategorize(id, update.category(), update.applyToMerchant());
    }

    @DeleteMapping("/transactions")
    public Map<String, Integer> deleteAll() {
        return Map.of("deleted", transactionService.deleteAll());
    }

    @GetMapping("/categories")
    public List<CategoryOption> categories() {
        return Arrays.stream(Category.values()).map(c -> new CategoryOption(c, c.label())).toList();
    }
}

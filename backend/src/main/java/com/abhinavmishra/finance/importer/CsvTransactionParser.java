package com.abhinavmishra.finance.importer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Parses bank CSV exports. Banks disagree on column names and on whether money
 * out is a negative "Amount" or a separate "Debit" column, so the header row is
 * inspected to find the date, description, and amount columns.
 */
@Component
public class CsvTransactionParser {

    private static final Set<String> DATE_HEADERS = Set.of("date", "posted date", "posting date", "transaction date", "trans. date");
    private static final Set<String> DESCRIPTION_HEADERS = Set.of("description", "payee", "memo", "name", "details", "merchant");
    private static final Set<String> AMOUNT_HEADERS = Set.of("amount", "transaction amount");
    private static final Set<String> DEBIT_HEADERS = Set.of("debit", "withdrawal", "withdrawals", "money out");
    private static final Set<String> CREDIT_HEADERS = Set.of("credit", "deposit", "deposits", "money in");

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy"));

    public List<ParsedTransaction> parse(Reader input) throws IOException {
        BufferedReader reader = new BufferedReader(input);
        String headerLine = reader.readLine();
        if (headerLine == null) {
            throw new CsvFormatException("file is empty");
        }
        Columns cols = Columns.from(splitLine(stripBom(headerLine)));

        List<ParsedTransaction> out = new ArrayList<>();
        String line;
        int lineNo = 1;
        while ((line = reader.readLine()) != null) {
            lineNo++;
            if (line.isBlank()) {
                continue;
            }
            List<String> fields = splitLine(line);
            try {
                out.add(new ParsedTransaction(
                        parseDate(field(fields, cols.date)),
                        field(fields, cols.description).replaceAll("\\s+", " ").trim(),
                        cols.amount(fields)));
            } catch (RuntimeException e) {
                throw new CsvFormatException("line " + lineNo + ": " + e.getMessage());
            }
        }
        return out;
    }

    private record Columns(int date, int description, int amount, int debit, int credit) {

        static Columns from(List<String> header) {
            int date = -1, desc = -1, amount = -1, debit = -1, credit = -1;
            for (int i = 0; i < header.size(); i++) {
                String h = header.get(i).trim().toLowerCase(Locale.ROOT);
                if (date < 0 && DATE_HEADERS.contains(h)) date = i;
                else if (desc < 0 && DESCRIPTION_HEADERS.contains(h)) desc = i;
                else if (amount < 0 && AMOUNT_HEADERS.contains(h)) amount = i;
                else if (debit < 0 && DEBIT_HEADERS.contains(h)) debit = i;
                else if (credit < 0 && CREDIT_HEADERS.contains(h)) credit = i;
            }
            if (date < 0 || desc < 0 || (amount < 0 && debit < 0 && credit < 0)) {
                throw new CsvFormatException(
                        "header must include a date, a description, and an amount (or debit/credit) column; got " + header);
            }
            return new Columns(date, desc, amount, debit, credit);
        }

        BigDecimal amount(List<String> fields) {
            if (amount >= 0) {
                return parseMoney(field(fields, amount));
            }
            BigDecimal out = BigDecimal.ZERO;
            if (debit >= 0 && !field(fields, debit).isBlank()) {
                out = out.subtract(parseMoney(field(fields, debit)).abs());
            }
            if (credit >= 0 && !field(fields, credit).isBlank()) {
                out = out.add(parseMoney(field(fields, credit)).abs());
            }
            return out;
        }
    }

    static BigDecimal parseMoney(String raw) {
        String s = raw.trim().replace("$", "").replace(",", "");
        boolean negative = false;
        if (s.startsWith("(") && s.endsWith(")")) { // accounting style (12.34)
            negative = true;
            s = s.substring(1, s.length() - 1);
        }
        if (s.isEmpty()) {
            throw new IllegalArgumentException("missing amount");
        }
        BigDecimal value = new BigDecimal(s);
        return negative ? value.negate() : value;
    }

    static LocalDate parseDate(String raw) {
        String s = raw.trim();
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(s, f);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        throw new IllegalArgumentException("unrecognized date '" + raw + "'");
    }

    private static String field(List<String> fields, int index) {
        return index < fields.size() ? fields.get(index) : "";
    }

    private static String stripBom(String s) {
        return s.startsWith("﻿") ? s.substring(1) : s;
    }

    /** Splits one CSV line, honoring double quotes and "" escapes. */
    static List<String> splitLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields;
    }
}

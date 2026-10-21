package com.abhinavmishra.finance.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class CsvTransactionParserTest {

    private final CsvTransactionParser parser = new CsvTransactionParser();

    @Test
    void parsesSignedAmountColumn() throws Exception {
        String csv = """
                Date,Description,Amount
                2026-09-01,"STARBUCKS #123, SALT LAKE",-5.25
                09/02/2026,PAYROLL,"1,500.00"
                """;
        List<ParsedTransaction> rows = parser.parse(new StringReader(csv));
        assertThat(rows).containsExactly(
                new ParsedTransaction(LocalDate.of(2026, 9, 1), "STARBUCKS #123, SALT LAKE", new BigDecimal("-5.25")),
                new ParsedTransaction(LocalDate.of(2026, 9, 2), "PAYROLL", new BigDecimal("1500.00")));
    }

    @Test
    void parsesSeparateDebitAndCreditColumns() throws Exception {
        String csv = """
                Posting Date,Payee,Debit,Credit,Balance
                9/3/26,SMITHS,42.10,,900.00
                9/4/26,REFUND,,$12.00,912.00
                """;
        List<ParsedTransaction> rows = parser.parse(new StringReader(csv));
        assertThat(rows).extracting(ParsedTransaction::amount)
                .containsExactly(new BigDecimal("-42.10"), new BigDecimal("12.00"));
    }

    @Test
    void handlesAccountingNegativesBomAndEscapedQuotes() throws Exception {
        String csv = "﻿Date,Memo,Amount\n2026-09-05,\"JOE \"\"THE\"\" BARBER\",(18.00)\n";
        ParsedTransaction row = parser.parse(new StringReader(csv)).get(0);
        assertThat(row.description()).isEqualTo("JOE \"THE\" BARBER");
        assertThat(row.amount()).isEqualByComparingTo("-18.00");
    }

    @Test
    void reportsLineNumberOnBadRow() {
        String csv = "Date,Description,Amount\n2026-09-01,OK,1.00\nnot-a-date,BAD,2.00\n";
        assertThatThrownBy(() -> parser.parse(new StringReader(csv)))
                .isInstanceOf(CsvFormatException.class)
                .hasMessageContaining("line 3");
    }

    @Test
    void rejectsUnknownHeader() {
        assertThatThrownBy(() -> parser.parse(new StringReader("Foo,Bar\n1,2\n")))
                .isInstanceOf(CsvFormatException.class);
    }
}

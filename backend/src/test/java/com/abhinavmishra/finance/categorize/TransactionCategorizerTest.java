package com.abhinavmishra.finance.categorize;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import com.abhinavmishra.finance.model.Category;
import com.abhinavmishra.finance.model.CategorySource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TransactionCategorizerTest {

    private final MerchantNormalizer normalizer = new MerchantNormalizer();
    private final TransactionCategorizer categorizer = new TransactionCategorizer(new ObjectMapper());

    private Category categorize(String description, String amount) {
        return categorizer.categorize(normalizer.normalize(description), description, new BigDecimal(amount)).category();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "POS PURCHASE SQ *BLUE COPPER 0412 SALT LAKE CTY UT | -6.75   | DINING",
            "SMITHS #4105 SANDY UT                              | -82.14  | GROCERIES",
            "NETFLIX.COM 866-579-7172 CA                        | -15.49  | SUBSCRIPTIONS",
            "ACME CORP PAYROLL PPD ID 99887                     | 1250.00 | INCOME",
            "UBER *TRIP HELP.UBER.COM                           | -14.20  | TRANSPORTATION",
            "UBER EATS                                          | -23.10  | DINING",
            "ZELLE TO JOHN DOE                                  | -40.00  | TRANSFER",
            "ROCKY MOUNTAIN POWER                               | -48.33  | UTILITIES",
            "MONTHLY SERVICE FEE                                | -12.00  | FEES",
            "AMZN Mktp US*2K4L19XY0                             | -29.99  | SHOPPING",
            "AMZN Mktp US*2K4L19XY0                             | 29.99   | SHOPPING",
            "UNIVERSITY OF UTAH TUITION                         | -4500.00| EDUCATION",
            "SOME UNKNOWN PLACE                                 | -10.00  | OTHER",
            "SOME UNKNOWN PLACE                                 | 10.00   | INCOME",
    })
    void appliesKeywordRules(String description, String amount, Category expected) {
        assertThat(categorize(description, amount)).isEqualTo(expected);
    }

    @Test
    void keywordsMatchWholeWordsOnly() {
        // "FEE" must not match inside "COFFEE"
        assertThat(categorize("LOCAL COFFEE ROASTERS", "-4.50")).isEqualTo(Category.DINING);
    }

    @Test
    void merchantOverrideBeatsRules() {
        var result = categorizer.categorize("SMITHS", "SMITHS #4105", new BigDecimal("-20.00"),
                Map.of("SMITHS", Category.HOUSING));
        assertThat(result.category()).isEqualTo(Category.HOUSING);
        assertThat(result.source()).isEqualTo(CategorySource.MERCHANT_OVERRIDE);
    }
}

package com.abhinavmishra.finance.categorize;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MerchantNormalizerTest {

    private final MerchantNormalizer normalizer = new MerchantNormalizer();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "POS PURCHASE SQ *BLUE COPPER 0412 SALT LAKE CTY UT | BLUE COPPER",
            "NETFLIX.COM 866-579-7172 CA                         | NETFLIX",
            "DEBIT CARD PURCHASE XXXX1234 SMITHS #4105 SANDY UT  | SMITHS",
            "TST* CAFE RIO 113 MIDVALE UT                        | CAFE RIO",
            "Spotify USA                                         | SPOTIFY USA",
            "AMZN Mktp US*2K4L19XY0                              | AMZN MKTP US",
            "PAYPAL *STEAM GAMES                                 | STEAM GAMES",
    })
    void normalizesNoisyDescriptions(String raw, String expected) {
        assertThat(normalizer.normalize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "SMITHS #4105 SANDY UT 10/02 | SMITHS #4410 SALT LAKE CTY UT 10/09",
            "SPOTIFY P1A2B3C4            | SPOTIFY P9Z8Y7X6",
    })
    void sameMerchantGetsSameKey(String a, String b) {
        assertThat(normalizer.normalize(a)).isEqualTo(normalizer.normalize(b));
    }
}

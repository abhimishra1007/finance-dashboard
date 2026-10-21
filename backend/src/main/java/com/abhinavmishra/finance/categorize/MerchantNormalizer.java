package com.abhinavmishra.finance.categorize;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Turns noisy bank descriptions into a stable merchant key, e.g.
 * "POS PURCHASE SQ *BLUE COPPER 0412 SALT LAKE CTY UT" -> "BLUE COPPER".
 * The same merchant then groups together for categorization overrides and
 * recurring-payment detection.
 */
@Component
public class MerchantNormalizer {

    private static final List<Pattern> PREFIXES = List.of(
            Pattern.compile("^(POS|DEBIT CARD|CHECKCARD|CHECK CARD|VISA|RECURRING|PREAUTHORIZED|ACH)( PURCHASE| DEBIT| PAYMENT| WITHDRAWAL)?\\s+"),
            Pattern.compile("^PURCHASE( AUTHORIZED ON \\d{2}/\\d{2})?\\s+"),
            Pattern.compile("^(SQ|TST|SP|PY|PAYPAL|GOOGLE|APL|IN|DD|BT)\\s?\\*\\s*"));

    private static final List<Pattern> NOISE = List.of(
            Pattern.compile("\\b(X{2,}|\\*{2,})\\d{2,}\\b"),          // masked card numbers
            Pattern.compile("\\b\\d{2}/\\d{2}(/\\d{2,4})?\\b"),        // dates
            Pattern.compile("#\\s?\\d+"),                              // store numbers
            Pattern.compile("\\b[A-Z]*\\d[A-Z\\d]*\\b"));              // reference ids, any token containing a digit

    private static final Pattern TRAILING_STATE =
            Pattern.compile(" (CA|NY|TX|UT|WA|OR|ID|AZ|NV|CO|FL|IL|MA|NJ|PA|GA|NC|VA|OH|MI|MN)$");

    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Z0-9& ]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    public String normalize(String description) {
        String s = description.toUpperCase(Locale.ROOT).trim();
        for (Pattern p : PREFIXES) {
            s = p.matcher(s).replaceFirst("");
        }
        // Keep the domain name itself (NETFLIX.COM -> NETFLIX) before stripping punctuation.
        s = s.replaceAll("\\b(?:WWW\\.)?([A-Z]+)\\.(?:COM|NET|ORG)\\b", "$1");
        for (Pattern p : NOISE) {
            s = p.matcher(s).replaceAll(" ");
        }
        s = NON_ALNUM.matcher(s).replaceAll(" ");
        s = SPACES.matcher(s).replaceAll(" ").trim();
        s = TRAILING_STATE.matcher(s).replaceFirst("");
        s = stripTrailingCity(s);

        // Most merchants are identified by their first few words.
        String[] words = s.split(" ");
        if (words.length > 3) {
            s = String.join(" ", words[0], words[1], words[2]);
        }
        return s.isEmpty() ? description.toUpperCase(Locale.ROOT).trim() : s;
    }

    /** Drops a trailing city name, e.g. "SMITHS SALT LAKE CTY" -> "SMITHS". */
    private static String stripTrailingCity(String s) {
        return s.replaceAll(" (SALT LAKE CTY|SALT LAKE CITY|SLC|SANDY|PROVO|OREM|OGDEN|MIDVALE|MURRAY|DRAPER|SEATTLE|SAN FRANCISCO|NEW YORK|LOS ANGELES)$", "");
    }
}

package com.matessa.commercial.service;

import org.springframework.stereotype.Component;

import java.util.function.Predicate;

/**
 * Builds a unique, human-readable SKU for a variant so staff never type one by hand.
 *
 * <p>Format: {@code <PRODUCT>[-<COLOR>][-<SIZE>]-<NN>}, uppercased and stripped of
 * non-alphanumerics. The colour and size segments are included only when present, so it
 * degrades cleanly for variants with size-but-no-colour, colour-but-no-size, or neither
 * (e.g. one-off artwork). Uniqueness is guaranteed by incrementing the {@code NN} suffix
 * until the supplied {@code exists} check passes.
 */
@Component
public class SkuGenerator {

    /**
     * @param productName the owning product's name (used for the leading code)
     * @param size        variant size, may be null/blank
     * @param color       variant colour, may be null/blank
     * @param exists      returns true if a candidate SKU is already taken
     */
    public String generate(String productName, String size, String color, Predicate<String> exists) {
        String product = productCode(productName);
        StringBuilder base = new StringBuilder(product);
        String colorCode = segment(color, 3);
        String sizeCode = segment(size, 3);
        if (!colorCode.isEmpty()) base.append('-').append(colorCode);
        if (!sizeCode.isEmpty()) base.append('-').append(sizeCode);

        String prefix = base.toString();
        for (int n = 1; ; n++) {
            String candidate = prefix + "-" + String.format("%02d", n);
            if (!exists.test(candidate)) {
                return candidate;
            }
        }
    }

    /** Word-initials for multi-word names (VENOM ATLAS HELMET → VAH), else the first
     *  few letters (SUNSET → SUN). Falls back to "SKU" if the name has no letters. */
    private String productCode(String name) {
        if (name == null) return "SKU";
        String[] words = name.trim().split("[^A-Za-z0-9]+");
        StringBuilder initials = new StringBuilder();
        for (String w : words) {
            String clean = w.replaceAll("[^A-Za-z0-9]", "");
            if (!clean.isEmpty()) initials.append(Character.toUpperCase(clean.charAt(0)));
        }
        if (initials.length() >= 2) {
            return initials.substring(0, Math.min(4, initials.length()));
        }
        String single = String.join("", words).replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        return single.isEmpty() ? "SKU" : single.substring(0, Math.min(4, single.length()));
    }

    private String segment(String value, int max) {
        if (value == null) return "";
        String clean = value.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}

package com.matessa.user.util;

import java.util.Locale;

/** Email addresses are case-insensitive identifiers: stored and matched lower-cased and trimmed. */
public final class Emails {

    private Emails() {}

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}

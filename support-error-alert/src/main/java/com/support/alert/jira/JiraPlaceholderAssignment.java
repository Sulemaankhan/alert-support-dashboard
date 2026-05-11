package com.support.alert.jira;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Random 4-digit batch id; one synthetic Jira-style key per batch (shared by up to four errors in the same ingest group).
 */
public final class JiraPlaceholderAssignment {

    private static final String KEY_PREFIX = "ALRT";
    /** Alerts are grouped by this size; each group shares one Jira batch number and one issue key. */
    public static final int ERRORS_PER_JIRA_GROUP = 4;

    private JiraPlaceholderAssignment() {
    }

    /** Four-digit string {@code 0000}–{@code 9999}. */
    public static String randomFourDigitBatchId() {
        int n = ThreadLocalRandom.current().nextInt(0, 10_000);
        return String.format("%04d", n);
    }

    /** Single placeholder key for the whole group of errors, e.g. {@code ALRT-7392}. */
    public static String issueKeyForBatch(String fourDigitBatchId) {
        return KEY_PREFIX + "-" + fourDigitBatchId;
    }
}

package com.support.alert.jira;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Stable random 4-digit id per group index (every {@link JiraPlaceholderAssignment#ERRORS_PER_JIRA_GROUP}
 * error-detail rows share one group). Cleared when the alert store is cleared.
 */
@Component
public class JiraGroupBatchRegistry {

    private final ConcurrentHashMap<Long, String> batchIdByGroupIndex = new ConcurrentHashMap<>();

    public String batchIdForGroup(long groupIndex) {
        return batchIdByGroupIndex.computeIfAbsent(
                groupIndex,
                g -> JiraPlaceholderAssignment.randomFourDigitBatchId());
    }

    public void clear() {
        batchIdByGroupIndex.clear();
    }
}

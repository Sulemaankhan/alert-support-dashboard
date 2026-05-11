package com.support.alert.store;

import com.support.alert.jira.JiraGroupBatchRegistry;
import com.support.alert.jira.JiraPlaceholderAssignment;
import com.support.alert.model.AlertRecord;
import com.support.alert.model.AlertStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class InMemoryAlertStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAlertStore.class);

    private final List<AlertRecord> alerts = new CopyOnWriteArrayList<>();
    private final JiraGroupBatchRegistry jiraGroupBatchRegistry;

    public InMemoryAlertStore(JiraGroupBatchRegistry jiraGroupBatchRegistry) {
        this.jiraGroupBatchRegistry = jiraGroupBatchRegistry;
    }

    /**
     * Alerts in arrival order (append order). Each consecutive block of four rows shares one Jira batch id.
     */
    public List<AlertRecord> findAllInArrivalOrder() {
        return new ArrayList<>(alerts);
    }

    public void addAll(List<AlertRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        int baseIndex = alerts.size();
        List<AlertRecord> stamped = new ArrayList<>(records.size());
        int groupSize = JiraPlaceholderAssignment.ERRORS_PER_JIRA_GROUP;
        for (int i = 0; i < records.size(); i++) {
            long groupIndex = (baseIndex + i) / groupSize;
            String batchId = jiraGroupBatchRegistry.batchIdForGroup(groupIndex);
            List<String> keys = List.of(JiraPlaceholderAssignment.issueKeyForBatch(batchId));
            stamped.add(records.get(i).withJiraAssignment(batchId, keys));
        }
        alerts.addAll(stamped);
        log.debug("Store addAll: +{} record(s); total={}", records.size(), alerts.size());
    }

    public int size() {
        return alerts.size();
    }

    public void clearAll() {
        alerts.clear();
        jiraGroupBatchRegistry.clear();
    }

    public Optional<AlertRecord> updateStatus(UUID id, AlertStatus status) {
        for (int i = 0; i < alerts.size(); i++) {
            AlertRecord existing = alerts.get(i);
            if (existing.getId().equals(id)) {
                AlertRecord updated = new AlertRecord(
                        existing.getId(),
                        existing.getErrorDetails(),
                        existing.getFunctionPath(),
                        existing.getFilePath(),
                        existing.getServiceName(),
                        existing.getSeverity(),
                        status,
                        existing.getCreatedAt(),
                        existing.getJiraBatchId(),
                        existing.getJiraIssueKeys()
                );
                alerts.set(i, updated);
                return Optional.of(updated);
            }
        }
        return Optional.empty();
    }
}

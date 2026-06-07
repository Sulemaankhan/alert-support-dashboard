package com.support.alert.store;

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

    public List<AlertRecord> findAllInArrivalOrder() {
        return new ArrayList<>(alerts);
    }

    public Optional<AlertRecord> findById(UUID id) {
        for (AlertRecord a : alerts) {
            if (a.getId().equals(id)) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }

    public void addAll(List<AlertRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        alerts.addAll(records);
        log.debug("Store addAll: +{} record(s); total={}", records.size(), alerts.size());
    }

    /**
     * Replaces all alerts with at most one record (JSON or email ingest).
     */
    public void replaceWithSingle(AlertRecord record) {
        alerts.clear();
        if (record != null) {
            alerts.add(record);
        }
        log.debug("Store replaceWithSingle: total={}", alerts.size());
    }

    public int size() {
        return alerts.size();
    }

    public void clearAll() {
        alerts.clear();
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

    /**
     * Links a created Jira issue key to the alert (used for filters and display).
     */
    public Optional<AlertRecord> attachJiraIssue(UUID id, String issueKey) {
        if (issueKey == null || issueKey.isBlank()) {
            return Optional.empty();
        }
        String key = issueKey.trim();
        for (int i = 0; i < alerts.size(); i++) {
            AlertRecord existing = alerts.get(i);
            if (existing.getId().equals(id)) {
                AlertRecord updated = existing.withJiraAssignment(key, List.of(key));
                alerts.set(i, updated);
                log.info("Attached Jira {} to alert {}", key, id);
                return Optional.of(updated);
            }
        }
        return Optional.empty();
    }
}

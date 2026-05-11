package com.supportalert.issue;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class IssueStore {

    private final Map<Long, Issue> issues = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(0);

    public Issue save(Issue issue) {
        long id = idSeq.incrementAndGet();
        Instant now = Instant.now();
        issue.setId(id);
        if (issue.getCreatedAt() == null) {
            issue.setCreatedAt(now);
        }
        issue.setUpdatedAt(now);
        issues.put(id, issue);
        return issue;
    }

    public Optional<Issue> findById(long id) {
        return Optional.ofNullable(issues.get(id));
    }

    public List<Issue> findAll() {
        return issues.values().stream()
                .sorted(Comparator.comparing(Issue::getId).reversed())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public Issue update(Issue issue) {
        issue.setUpdatedAt(Instant.now());
        issues.put(issue.getId(), issue);
        return issue;
    }

    public void seedIfEmpty(List<Issue> seed) {
        if (!issues.isEmpty()) {
            return;
        }
        for (Issue s : seed) {
            long id = idSeq.incrementAndGet();
            s.setId(id);
            Instant now = Instant.now();
            if (s.getCreatedAt() == null) {
                s.setCreatedAt(now);
            }
            s.setUpdatedAt(now);
            issues.put(id, s);
        }
    }

    public long countOpen() {
        return issues.values().stream()
                .filter(i -> i.getStatus() == IssueStatus.OPEN || i.getStatus() == IssueStatus.IN_PROGRESS)
                .count();
    }
}

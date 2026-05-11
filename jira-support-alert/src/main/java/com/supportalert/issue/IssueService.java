package com.supportalert.issue;

import com.supportalert.alert.AlertBroker;
import com.supportalert.alert.AlertEvent;
import com.supportalert.alert.AlertType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class IssueService {

    private static final String USER_MANAGER = "UserManager.java";

    private final IssueStore store;
    private final AlertBroker alertBroker;

    public IssueService(IssueStore store, AlertBroker alertBroker) {
        this.store = store;
        this.alertBroker = alertBroker;
    }

    public void seedFromUserManagerSample() {
        List<Issue> seed = List.of(
                issue("BUG-1", "Constructor never initializes `users` list",
                        "Calling addUser before assignment causes NullPointerException.",
                        15, 18, IssueSeverity.CRITICAL),
                issue("BUG-2", "addUser does not validate null name",
                        "Null or blank names can corrupt state or cause NPE downstream.",
                        20, 24, IssueSeverity.HIGH),
                issue("BUG-3", "getUser omits bounds checking",
                        "Invalid index throws IndexOutOfBoundsException.",
                        26, 30, IssueSeverity.HIGH),
                issue("BUG-4", "findUser compares strings with ==",
                        "Reference equality fails for distinct String instances with same content.",
                        32, 41, IssueSeverity.MEDIUM),
                issue("BUG-5", "findUser may NPE when name is null",
                        "Loop compares with possibly null parameter without guard.",
                        32, 41, IssueSeverity.MEDIUM),
                issue("BUG-6", "clearList assigns null instead of clearing",
                        "Subsequent operations NPE; prefer clear() or new ArrayList<>().",
                        43, 47, IssueSeverity.MEDIUM)
        );
        store.seedIfEmpty(seed);
    }

    private Issue issue(String code, String title, String desc, int lineStart, int lineEnd,
                        IssueSeverity severity) {
        Issue i = new Issue();
        i.setCodeRef(code);
        i.setTitle(title);
        i.setDescription(desc);
        i.setComponent("UserManager");
        i.setFilePath(USER_MANAGER);
        i.setLineStart(lineStart);
        i.setLineEnd(lineEnd);
        i.setSeverity(severity);
        i.setStatus(IssueStatus.OPEN);
        return i;
    }

    public List<Issue> list() {
        return store.findAll();
    }

    public Optional<Issue> get(long id) {
        return store.findById(id);
    }

    public Issue create(CreateIssueRequest req) {
        Issue i = new Issue();
        i.setCodeRef("CUSTOM-" + System.currentTimeMillis());
        i.setTitle(req.getTitle());
        i.setDescription(req.getDescription());
        i.setComponent(Optional.ofNullable(req.getComponent()).orElse("UserManager"));
        i.setFilePath(Optional.ofNullable(req.getFilePath()).orElse(USER_MANAGER));
        i.setLineStart(req.getLineStart());
        i.setLineEnd(req.getLineEnd());
        i.setSeverity(req.getSeverity());
        i.setStatus(IssueStatus.OPEN);
        Issue saved = store.save(i);
        alertBroker.publish(new AlertEvent(
                AlertType.NEW_ISSUE,
                "New issue tracked: " + saved.getTitle(),
                saved.getId(),
                saved.getSeverity().name()
        ));
        return saved;
    }

    public Optional<Issue> update(long id, UpdateIssueRequest req) {
        return store.findById(id).map(existing -> {
            if (req.getStatus() != null) {
                existing.setStatus(req.getStatus());
            }
            if (req.getSeverity() != null) {
                existing.setSeverity(req.getSeverity());
            }
            if (req.getTitle() != null) {
                existing.setTitle(req.getTitle());
            }
            if (req.getDescription() != null) {
                existing.setDescription(req.getDescription());
            }
            Issue updated = store.update(existing);
            alertBroker.publish(new AlertEvent(
                    AlertType.ISSUE_UPDATED,
                    "Issue updated: " + updated.getTitle(),
                    updated.getId(),
                    updated.getStatus().name()
            ));
            return updated;
        });
    }

    public long openCount() {
        return store.countOpen();
    }
}

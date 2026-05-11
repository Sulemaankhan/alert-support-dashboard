package com.supportalert.issue;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/issues")
public class IssueController {

    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    @GetMapping
    public List<Issue> list() {
        return issueService.list();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Issue> get(@PathVariable long id) {
        return issueService.get(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Issue create(@Valid @RequestBody CreateIssueRequest body) {
        return issueService.create(body);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Issue> patch(@PathVariable long id, @RequestBody UpdateIssueRequest body) {
        return issueService.update(id, body)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats/summary")
    public Map<String, Object> summary() {
        List<Issue> all = issueService.list();
        long open = issueService.openCount();
        return Map.of(
                "total", all.size(),
                "openOrInProgress", open,
                "bySeverity", Map.of(
                        "CRITICAL", all.stream().filter(i -> i.getSeverity() == IssueSeverity.CRITICAL).count(),
                        "HIGH", all.stream().filter(i -> i.getSeverity() == IssueSeverity.HIGH).count(),
                        "MEDIUM", all.stream().filter(i -> i.getSeverity() == IssueSeverity.MEDIUM).count(),
                        "LOW", all.stream().filter(i -> i.getSeverity() == IssueSeverity.LOW).count()
                )
        );
    }
}

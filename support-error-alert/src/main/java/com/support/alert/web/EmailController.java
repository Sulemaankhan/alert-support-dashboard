package com.support.alert.web;

import com.support.alert.email.ImapInboxService;
import com.support.alert.email.InboxSearchResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/email")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class EmailController {

    private final ImapInboxService inboxService;

    public EmailController(ImapInboxService inboxService) {
        this.inboxService = inboxService;
    }

    /**
     * Returns inbox messages whose subject contains the given text (IMAP SUBJECT search).
     */
    @GetMapping("/inbox")
    public ResponseEntity<InboxSearchResponse> inboxBySubject(@RequestParam("subject") String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Query parameter 'subject' is required and must not be blank.");
        }
        InboxSearchResponse body = inboxService.searchBySubject(subject.trim());
        return ResponseEntity.ok(body);
    }
}

package com.support.alert.web;

import com.support.alert.auth.AuthContext;
import com.support.alert.auth.AuthUser;
import com.support.alert.email.ImapInboxService;
import com.support.alert.email.InboxSearchRequest;
import com.support.alert.email.InboxSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/email")
public class EmailController {

    private static final Logger log = LoggerFactory.getLogger(EmailController.class);

    private final ImapInboxService inboxService;

    public EmailController(ImapInboxService inboxService) {
        this.inboxService = inboxService;
    }

    @PostMapping(value = {"/inbox", "/inbox/search"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InboxSearchResponse> inboxBySubject(@RequestBody(required = false) InboxSearchRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        Optional<AuthUser> userOpt = AuthContext.currentUser();
        String signedInEmail = userOpt.map(AuthUser::getEmail).orElse(null);
        String googleToken = null;
        if (userOpt.isPresent() && userOpt.get().isGmailInboxAccess()) {
            googleToken = userOpt.get().getGoogleAccessToken();
        } else if (userOpt.isPresent()) {
            log.debug(
                    "Inbox search for {} without Gmail token (gmailInboxAccess={})",
                    mask(signedInEmail),
                    userOpt.get().isGmailInboxAccess());
        }

        InboxSearchResponse body = inboxService.searchBySubject(request, signedInEmail, googleToken);
        return ResponseEntity.ok(body);
    }

    private static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "(unknown)";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}

package com.support.alert.email;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.SubjectTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

@Service
public class ImapInboxService {

    private static final Logger log = LoggerFactory.getLogger(ImapInboxService.class);

    private final ImapEmailProperties props;

    public ImapInboxService(ImapEmailProperties props) {
        this.props = props;
    }

    public InboxSearchResponse searchBySubject(InboxSearchRequest request, String signedInEmail, String googleAccessToken) {
        if (request == null) {
            return InboxSearchResponse.error("", "Request body is required.");
        }
        String subject = request.getSubject() != null ? request.getSubject().trim() : "";
        if (subject.isEmpty()) {
            return InboxSearchResponse.error("", "Subject is required.");
        }
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        String password = request.getPassword() != null ? request.getPassword() : "";
        if (email.isEmpty()) {
            return InboxSearchResponse.error(subject, "Email address is required.");
        }

        if (password.isBlank()) {
            if (signedInEmail == null || signedInEmail.isBlank()) {
                return InboxSearchResponse.error(
                        subject,
                        "Sign in with Google first, then search inbox without a password.");
            }
            if (!signedInEmail.equalsIgnoreCase(email)) {
                return InboxSearchResponse.error(
                        subject,
                        "Use the same email you signed in with (" + signedInEmail + ").");
            }
            if (googleAccessToken == null || googleAccessToken.isBlank()) {
                return InboxSearchResponse.error(
                        subject,
                        "Gmail access expired or was not granted. Sign out, sign in with Google again, "
                                + "and allow Gmail access when prompted.");
            }
            ImapSessionConfig session = resolveOAuthSession(request, email, googleAccessToken);
            return searchWithSession(subject, session);
        }

        ImapSessionConfig session = resolveSession(request, email, password);
        return searchWithSession(subject, session);
    }

    private ImapSessionConfig resolveOAuthSession(InboxSearchRequest request, String email, String accessToken) {
        String host = resolveHost(request, email);
        int port = resolvePort(request);
        String folder = props.getFolder() != null && !props.getFolder().isBlank() ? props.getFolder() : "INBOX";
        int maxResults = props.getMaxResults() > 0 ? props.getMaxResults() : 50;
        return new ImapSessionConfig(host, port, email, folder, maxResults, accessToken);
    }

    private ImapSessionConfig resolveSession(InboxSearchRequest request, String email, String password) {
        String host = resolveHost(request, email);
        int port = resolvePort(request);
        String folder = props.getFolder() != null && !props.getFolder().isBlank() ? props.getFolder() : "INBOX";
        int maxResults = props.getMaxResults() > 0 ? props.getMaxResults() : 50;
        return new ImapSessionConfig(host, port, email, password, folder, maxResults);
    }

    private String resolveHost(InboxSearchRequest request, String email) {
        String host = request.getHost() != null ? request.getHost().trim() : "";
        if (host.isEmpty()) {
            host = defaultHostForEmail(email);
        }
        return host;
    }

    private int resolvePort(InboxSearchRequest request) {
        int port = request.getPort() != null && request.getPort() > 0 ? request.getPort() : props.getPort();
        if (port <= 0) {
            port = 993;
        }
        return port;
    }

    private static String defaultHostForEmail(String email) {
        String lower = email.toLowerCase();
        if (lower.endsWith("@gmail.com") || lower.endsWith("@googlemail.com")) {
            return "imap.gmail.com";
        }
        if (lower.endsWith("@outlook.com") || lower.endsWith("@hotmail.com") || lower.endsWith("@live.com")) {
            return "outlook.office365.com";
        }
        if (lower.endsWith("@yahoo.com")) {
            return "imap.mail.yahoo.com";
        }
        return "imap.gmail.com";
    }

    private InboxSearchResponse searchWithSession(String subject, ImapSessionConfig session) {
        Properties mailProps = new Properties();
        mailProps.put("mail.store.protocol", "imaps");
        mailProps.put("mail.imaps.host", session.getHost());
        mailProps.put("mail.imaps.port", String.valueOf(session.getPort()));
        mailProps.put("mail.imaps.ssl.enable", "true");
        if (session.isOauth2()) {
            // Jakarta Mail: pass the OAuth access token as the "password" with only XOAUTH2 enabled.
            mailProps.put("mail.imaps.auth.mechanisms", "XOAUTH2");
            mailProps.put("mail.imaps.auth.login.disable", "true");
            mailProps.put("mail.imaps.auth.plain.disable", "true");
        }

        Session mailSession = Session.getInstance(mailProps);
        mailSession.setDebug(false);

        Store store = null;
        Folder folder = null;
        try {
            store = mailSession.getStore("imaps");
            if (session.isOauth2()) {
                store.connect(session.getHost(), session.getUsername(), session.getOauth2AccessToken());
            } else {
                store.connect(session.getHost(), session.getUsername(), session.getPassword());
            }
            folder = store.getFolder(session.getFolder());
            if (folder == null || !folder.exists()) {
                return InboxSearchResponse.error(subject, "Mail folder not found: " + session.getFolder());
            }
            folder.open(Folder.READ_ONLY);

            Message[] found = folder.search(new SubjectTerm(subject));
            if (found == null || found.length == 0) {
                return InboxSearchResponse.ok(subject, Collections.emptyList());
            }

            Arrays.sort(found, Comparator.comparing(this::safeReceived).reversed());
            int cap = Math.max(1, session.getMaxResults());
            List<InboxMessageDto> out = new ArrayList<>(Math.min(found.length, cap));
            for (int i = 0; i < found.length && i < cap; i++) {
                out.add(toDto(found[i]));
            }
            log.debug(
                    "IMAP subject search '{}' for {} returned {} message(s) (capped at {})",
                    subject,
                    maskEmail(session.getUsername()),
                    found.length,
                    cap);
            return InboxSearchResponse.ok(subject, out);
        } catch (MessagingException ex) {
            log.warn("IMAP inbox lookup failed for {}: {}", maskEmail(session.getUsername()), ex.getMessage());
            return InboxSearchResponse.error(subject, mapImapError(ex));
        } catch (Exception ex) {
            log.warn("Unexpected error during inbox lookup", ex);
            return InboxSearchResponse.error(subject, "Inbox lookup failed: " + ex.getMessage());
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "(unknown)";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String mapImapError(MessagingException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
        String lower = msg.toLowerCase();
        if (lower.contains("invalid credentials") || lower.contains("authenticationfailed")) {
            return "Mail server rejected the login (invalid credentials). For Gmail: enable IMAP, use 2-Step "
                    + "Verification, then create an App Password at https://myaccount.google.com/apppasswords "
                    + "(16 characters, no spaces). Use your full email address as the username.";
        }
        if (lower.contains("oauth2") && lower.contains("more")) {
            return "Gmail rejected the OAuth token (missing mail.google.com scope). Sign out, sign in with Google "
                    + "again, and allow full Gmail access when prompted.";
        }
        return "Could not read inbox: " + msg;
    }

    private long safeReceived(Message m) {
        try {
            if (m.getReceivedDate() != null) {
                return m.getReceivedDate().getTime();
            }
            if (m.getSentDate() != null) {
                return m.getSentDate().getTime();
            }
        } catch (MessagingException ignored) {
            /* fall through */
        }
        return 0L;
    }

    private InboxMessageDto toDto(Message m) throws MessagingException {
        String messageId = null;
        try {
            messageId = m.getHeader("Message-ID") != null && m.getHeader("Message-ID").length > 0
                    ? m.getHeader("Message-ID")[0]
                    : null;
        } catch (MessagingException ignored) {
            /* optional header */
        }
        String subj = safeSubject(m);
        String from = formatFrom(m.getFrom());
        String sentAt = null;
        if (m.getSentDate() != null) {
            sentAt = Instant.ofEpochMilli(m.getSentDate().getTime()).toString();
        } else if (m.getReceivedDate() != null) {
            sentAt = Instant.ofEpochMilli(m.getReceivedDate().getTime()).toString();
        }
        String preview = extractPreview(m);
        return new InboxMessageDto(messageId, subj, from, sentAt, preview);
    }

    private static String safeSubject(Message m) {
        try {
            String s = m.getSubject();
            return s != null ? s : "";
        } catch (MessagingException e) {
            return "";
        }
    }

    private static String formatFrom(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return "";
        }
        Address a = addresses[0];
        if (a instanceof InternetAddress) {
            InternetAddress ia = (InternetAddress) a;
            String personal = ia.getPersonal();
            String addr = ia.getAddress();
            if (personal != null && !personal.trim().isEmpty()) {
                return personal + " <" + addr + ">";
            }
            return addr != null ? addr : a.toString();
        }
        return a.toString();
    }

    private static String extractPreview(Message m) {
        try {
            Object content = m.getContent();
            String text = extractText(content, 0);
            return truncate(text, 600);
        } catch (Exception e) {
            return "";
        }
    }

    private static String extractText(Object content, int depth) throws Exception {
        if (content == null || depth > 8) {
            return "";
        }
        if (content instanceof String) {
            return (String) content;
        }
        if (content instanceof Multipart) {
            Multipart mp = (Multipart) content;
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart part = mp.getBodyPart(i);
                if (part.isMimeType("text/plain")) {
                    Object c = part.getContent();
                    if (c instanceof String) {
                        String s = (String) c;
                        if (!s.trim().isEmpty()) {
                            return s;
                        }
                    }
                }
            }
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart part = mp.getBodyPart(i);
                if (part.isMimeType("text/html")) {
                    Object c = part.getContent();
                    if (c instanceof String) {
                        String s = (String) c;
                        if (!s.trim().isEmpty()) {
                            return s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
                        }
                    }
                }
            }
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart part = mp.getBodyPart(i);
                Object c = part.getContent();
                String nested = extractText(c, depth + 1);
                if (!nested.trim().isEmpty()) {
                    return nested;
                }
            }
        }
        return "";
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "…";
    }

    private static void closeQuietly(Folder folder) {
        if (folder != null && folder.isOpen()) {
            try {
                folder.close(false);
            } catch (MessagingException e) {
                log.debug("Folder close: {}", e.getMessage());
            }
        }
    }

    private static void closeQuietly(Store store) {
        if (store != null && store.isConnected()) {
            try {
                store.close();
            } catch (MessagingException e) {
                log.debug("Store close: {}", e.getMessage());
            }
        }
    }
}

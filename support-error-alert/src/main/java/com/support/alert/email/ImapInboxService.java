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

    public InboxSearchResponse searchBySubject(String subject) {
        if (!props.isEnabled()) {
            return InboxSearchResponse.disabled(subject);
        }
        if (!props.isRunnable()) {
            return InboxSearchResponse.notConfigured(subject);
        }

        Properties mailProps = new Properties();
        mailProps.put("mail.store.protocol", "imaps");
        mailProps.put("mail.imaps.host", props.getHost());
        mailProps.put("mail.imaps.port", String.valueOf(props.getPort()));
        mailProps.put("mail.imaps.ssl.enable", "true");

        Session session = Session.getInstance(mailProps);
        session.setDebug(false);

        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore("imaps");
            store.connect(props.getHost(), props.getUsername(), props.getPassword());
            folder = store.getFolder(props.getFolder());
            if (folder == null || !folder.exists()) {
                return InboxSearchResponse.error(subject, "Mail folder not found: " + props.getFolder());
            }
            folder.open(Folder.READ_ONLY);

            Message[] found = folder.search(new SubjectTerm(subject));
            if (found == null || found.length == 0) {
                return InboxSearchResponse.ok(subject, Collections.emptyList());
            }

            Arrays.sort(found, Comparator.comparing(this::safeReceived).reversed());
            int cap = Math.max(1, props.getMaxResults());
            List<InboxMessageDto> out = new ArrayList<>(Math.min(found.length, cap));
            for (int i = 0; i < found.length && i < cap; i++) {
                out.add(toDto(found[i]));
            }
            log.debug("IMAP subject search '{}' returned {} message(s) (capped at {})", subject, found.length, cap);
            return InboxSearchResponse.ok(subject, out);
        } catch (MessagingException ex) {
            log.warn("IMAP inbox lookup failed: {}", ex.getMessage());
            return InboxSearchResponse.error(subject, "Could not read inbox: " + ex.getMessage());
        } catch (Exception ex) {
            log.warn("Unexpected error during inbox lookup", ex);
            return InboxSearchResponse.error(subject, "Inbox lookup failed: " + ex.getMessage());
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
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

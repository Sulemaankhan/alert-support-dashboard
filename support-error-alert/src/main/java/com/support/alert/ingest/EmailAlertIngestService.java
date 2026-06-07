package com.support.alert.ingest;

import com.support.alert.email.InboxMessageDto;
import com.support.alert.model.AlertRecord;
import com.support.alert.model.AlertSeverity;
import com.support.alert.model.AlertStatus;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class EmailAlertIngestService {

    private static final Logger log = LoggerFactory.getLogger(EmailAlertIngestService.class);

    public AlertRecord toAlert(InboxMessageDto message) {
        if (message == null) {
            throw new IllegalArgumentException("Email message body is required.");
        }
        String subject = nullToEmpty(message.getSubject());
        String preview = nullToEmpty(message.getPreview());
        String from = resolveSender(message.getFrom());
        String receivedAt = formatReceivedAt(message.getSentAt());

        if (from.isEmpty()) {
            log.warn("Email ingest missing sender address (subject={})", subject);
        }

        String inboxMessage = !preview.isEmpty() ? preview.trim() : subject;
        if (inboxMessage.isEmpty()) {
            inboxMessage = "(empty inbox message)";
        }

        String serviceName = !subject.isEmpty() ? subject : "email-inbox";

        return new AlertRecord(
                UUID.randomUUID(),
                inboxMessage,
                from,
                receivedAt,
                serviceName,
                AlertSeverity.MEDIUM,
                AlertStatus.OPEN,
                Instant.now(),
                "email",
                List.of());
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    /** Same display as inbox search "From" column (name + address when available). */
    private static String resolveSender(String from) {
        String trimmed = nullToEmpty(from).trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        try {
            InternetAddress[] parsed = InternetAddress.parse(trimmed, false);
            if (parsed.length > 0) {
                InternetAddress first = parsed[0];
                String personal = first.getPersonal();
                String address = first.getAddress();
                if (personal != null && !personal.isBlank() && address != null && !address.isBlank()) {
                    return personal.trim() + " <" + address.trim() + ">";
                }
                if (address != null && !address.isBlank()) {
                    return address.trim();
                }
            }
        } catch (Exception ex) {
            log.debug("Could not parse sender '{}': {}", trimmed, ex.getMessage());
        }
        return trimmed;
    }

    private static String formatReceivedAt(String sentAt) {
        if (sentAt == null || sentAt.isBlank()) {
            return "";
        }
        try {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.parse(sentAt.trim()));
        } catch (Exception ex) {
            return sentAt.trim();
        }
    }
}

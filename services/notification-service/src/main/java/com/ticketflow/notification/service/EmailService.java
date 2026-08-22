package com.ticketflow.notification.service;

import com.ticketflow.notification.entity.NotificationLog;
import com.ticketflow.notification.repository.NotificationLogRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

/**
 * SMTP delivery with an inline-image attachment path for the QR code.
 *
 * <p>The QR is attached as an <b>inline CID resource</b> rather than a base64
 * data-URI because Gmail, Outlook and most mobile clients silently strip
 * {@code data:} images in HTML mail — the ticket would arrive as a broken
 * image icon. CID attachments render everywhere and survive forwarding.
 *
 * <p>Every send is logged, and {@link #alreadySent} lets consumers skip
 * redeliveries: RabbitMQ is at-least-once, so without this a network blip
 * during ack would email the customer their ticket twice.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";

    private final JavaMailSender mailSender;
    private final NotificationLogRepository logRepository;
    private final String fromAddress;
    private final String fromName;
    private final boolean deliveryEnabled;

    public EmailService(JavaMailSender mailSender,
                        NotificationLogRepository logRepository,
                        @Value("${notification.from-address}") String fromAddress,
                        @Value("${notification.from-name}") String fromName,
                        @Value("${notification.delivery-enabled}") boolean deliveryEnabled) {
        this.mailSender = mailSender;
        this.logRepository = logRepository;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.deliveryEnabled = deliveryEnabled;
    }

    @Transactional(readOnly = true)
    public boolean alreadySent(String type, String dedupeKey) {
        return logRepository.existsByTypeAndDedupeKeyAndStatus(type, dedupeKey, STATUS_SENT);
    }

    /**
     * @param qrPng optional inline PNG; when present it is referenced from the
     *              HTML body as {@code <img src="cid:qrcode">}
     */
    @Transactional
    public void send(String type, String dedupeKey, String to, String subject, String html, byte[] qrPng) {
        if (alreadySent(type, dedupeKey)) {
            log.info("Skipping duplicate {} for {} (already delivered)", type, dedupeKey);
            return;
        }

        if (!deliveryEnabled) {
            log.info("MAIL_DELIVERY_ENABLED=false — {} for {} rendered but not sent", type, dedupeKey);
            record(type, dedupeKey, to, subject, STATUS_SKIPPED, "delivery disabled");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            if (qrPng != null && qrPng.length > 0) {
                helper.addInline("qrcode", new ByteArrayResource(qrPng), "image/png");
            }

            mailSender.send(message);
            record(type, dedupeKey, to, subject, STATUS_SENT, null);
            log.info("Sent {} to {} ({})", type, to, dedupeKey);

        } catch (UnsupportedEncodingException ex) {
            record(type, dedupeKey, to, subject, STATUS_FAILED, ex.getMessage());
            throw new IllegalStateException("Bad from-address encoding", ex);
        } catch (Exception ex) {
            record(type, dedupeKey, to, subject, STATUS_FAILED, ex.getMessage());
            // Rethrow so the listener nacks and the message retries / dead-letters.
            throw new IllegalStateException("Email delivery failed for " + dedupeKey, ex);
        }
    }

    private void record(String type, String dedupeKey, String to, String subject, String status, String error) {
        logRepository.save(NotificationLog.builder()
                .id(UUID.randomUUID())
                .type(type)
                .dedupeKey(dedupeKey)
                .recipient(to)
                .subject(subject)
                .status(status)
                .errorDetail(error)
                .build());
    }
}

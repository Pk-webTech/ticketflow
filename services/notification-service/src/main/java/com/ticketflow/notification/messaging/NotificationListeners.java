package com.ticketflow.notification.messaging;

import com.ticketflow.notification.config.RabbitConfig;
import com.ticketflow.notification.service.EmailService;
import com.ticketflow.notification.service.EmailTemplateRenderer;
import com.ticketflow.notification.service.QrCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The three email triggers.
 *
 * <p>Each handler is idempotent via {@code dedupeKey} (booking reference or
 * offer id), which matters because RabbitMQ delivers at-least-once — a
 * consumer that crashes after sending but before acking will see the message
 * again on restart.
 *
 * <p>Exceptions are allowed to escape: the queues are configured with retry
 * then dead-letter, so a transient SMTP outage is retried with backoff and a
 * permanently bad message ends up in the DLQ instead of blocking the queue.
 */
@Component
public class NotificationListeners {

    private static final Logger log = LoggerFactory.getLogger(NotificationListeners.class);

    private final EmailService emailService;
    private final EmailTemplateRenderer renderer;
    private final QrCodeService qrCodeService;

    public NotificationListeners(EmailService emailService,
                                 EmailTemplateRenderer renderer,
                                 QrCodeService qrCodeService) {
        this.emailService = emailService;
        this.renderer = renderer;
        this.qrCodeService = qrCodeService;
    }

    @RabbitListener(queues = RabbitConfig.Q_BOOKING_CONFIRMED)
    public void onBookingConfirmed(Events.BookingConfirmed event) {
        log.info("Preparing QR ticket for booking {}", event.bookingReference());

        // The QR encodes the booking reference only — no PII on a printed ticket.
        byte[] qr = qrCodeService.generatePng(event.bookingReference());

        Map<String, Object> vars = new HashMap<>();
        vars.put("customerName", event.customerName());
        vars.put("bookingReference", event.bookingReference());
        vars.put("eventTitle", event.eventTitle());
        vars.put("venueName", event.venueName());
        vars.put("showTime", EmailTemplateRenderer.formatInstant(event.showStartsAt()));
        vars.put("seats", event.seats() == null ? List.of() : event.seats());
        vars.put("totalAmount", event.totalAmount() == null ? BigDecimal.ZERO : event.totalAmount());

        String html = renderer.render("booking-confirmed", vars);

        emailService.send(
                "BOOKING_CONFIRMED",
                event.bookingReference(),
                event.customerEmail(),
                "Your tickets for " + event.eventTitle() + " (" + event.bookingReference() + ")",
                html,
                qr
        );
    }

    @RabbitListener(queues = RabbitConfig.Q_BOOKING_CANCELLED)
    public void onBookingCancelled(Events.BookingCancelled event) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("customerName", event.customerName());
        vars.put("bookingReference", event.bookingReference());
        vars.put("eventTitle", event.eventTitle());
        vars.put("venueName", event.venueName());
        vars.put("showTime", EmailTemplateRenderer.formatInstant(event.showStartsAt()));
        vars.put("seats", event.freedSeats() == null ? List.of() : event.freedSeats());
        vars.put("refundAmount", event.refundAmount() == null ? BigDecimal.ZERO : event.refundAmount());

        String html = renderer.render("booking-cancelled", vars);

        emailService.send(
                "BOOKING_CANCELLED",
                event.bookingReference(),
                event.customerEmail(),
                "Booking cancelled — " + event.bookingReference(),
                html,
                null
        );
    }

    @RabbitListener(queues = RabbitConfig.Q_WAITLIST_OFFER)
    public void onWaitlistOffer(Events.WaitlistOfferCreated event) {
        log.info("Sending time-limited seat offer {} (expires {})", event.offerId(), event.expiresAt());

        Map<String, Object> vars = new HashMap<>();
        vars.put("customerName", event.customerName());
        vars.put("eventTitle", event.eventTitle());
        vars.put("venueName", event.venueName());
        vars.put("showTime", EmailTemplateRenderer.formatInstant(event.showStartsAt()));
        vars.put("categoryName", event.categoryName());
        vars.put("seatLabels", event.seatLabels() == null ? List.of() : event.seatLabels());
        vars.put("claimUrl", event.claimUrl());
        vars.put("expiresAt", EmailTemplateRenderer.formatInstant(event.expiresAt()));
        vars.put("minutes", Math.max(1, event.ttlSeconds() / 60));

        String html = renderer.render("waitlist-offer", vars);

        emailService.send(
                "WAITLIST_OFFER",
                event.offerId().toString(),
                event.customerEmail(),
                "A seat just opened up for " + event.eventTitle() + " — claim it within "
                        + Math.max(1, event.ttlSeconds() / 60) + " minutes",
                html,
                null
        );
    }
}

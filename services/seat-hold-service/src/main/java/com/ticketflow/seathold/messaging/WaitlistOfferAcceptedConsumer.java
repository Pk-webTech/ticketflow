package com.ticketflow.seathold.messaging;

import com.ticketflow.seathold.exception.SeatUnavailableException;
import com.ticketflow.seathold.service.SeatHoldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class WaitlistOfferAcceptedConsumer {

    private static final Logger log = LoggerFactory.getLogger(WaitlistOfferAcceptedConsumer.class);

    private final SeatHoldService seatHoldService;

    public WaitlistOfferAcceptedConsumer(SeatHoldService seatHoldService) {
        this.seatHoldService = seatHoldService;
    }

    @RabbitListener(queues = "seathold.waitlist-offer-accepted.q")
    public void onWaitlistOfferAccepted(WaitlistOfferAcceptedEvent event) {
        log.info("Waitlist offer accepted: offerId={} show={} customer={} seats={}",
                event.offerId(), event.showId(), event.customerId(), event.seatIds());

        try {
            seatHoldService.createHold(event.showId(), event.seatIds(), event.customerId());
        } catch (SeatUnavailableException ex) {
            // Race: seat got taken between offer creation and acceptance. waitlist-service
            // is expected to fall through to the next person in line on this failure —
            // logged here for visibility; no retry, letting the message be dead-lettered
            // per the queue's DLX config so it isn't silently dropped.
            log.warn("Could not convert waitlist offer {} into a hold — seat no longer available: {}",
                    event.offerId(), ex.getMessage());
            throw ex;
        }
    }
}

package com.ticketflow.waitlist.controller;

import com.ticketflow.waitlist.dto.JoinWaitlistRequest;
import com.ticketflow.waitlist.dto.OfferResponse;
import com.ticketflow.waitlist.dto.WaitlistEntryResponse;
import com.ticketflow.waitlist.security.CurrentUser;
import com.ticketflow.waitlist.service.WaitlistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/waitlist")
public class WaitlistController {

    private final WaitlistService waitlistService;

    public WaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    /** Join the queue for a sold-out seat category. */
    @PostMapping
    public ResponseEntity<WaitlistEntryResponse> join(@Valid @RequestBody JoinWaitlistRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(waitlistService.join(request, CurrentUser.get()));
    }

    @GetMapping("/me")
    public List<WaitlistEntryResponse> myEntries() {
        return waitlistService.myEntries(CurrentUser.get().userId());
    }

    @DeleteMapping("/{entryId}")
    public ResponseEntity<Void> leave(@PathVariable UUID entryId) {
        waitlistService.leave(entryId, CurrentUser.get());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/shows/{showId}/categories/{categoryId}/length")
    public Map<String, Long> queueLength(@PathVariable UUID showId, @PathVariable UUID categoryId) {
        return Map.of("waiting", waitlistService.queueLength(showId, categoryId));
    }

    /** Public — the token in the emailed link is the credential. */
    @GetMapping("/offers/token/{token}")
    public OfferResponse viewOffer(@PathVariable String token) {
        return waitlistService.viewOffer(token);
    }

    /** Requires login: we must know WHO is claiming, and it must match the offer. */
    @PostMapping("/offers/token/{token}/accept")
    public OfferResponse acceptOffer(@PathVariable String token) {
        return waitlistService.acceptOffer(token, CurrentUser.get());
    }
}

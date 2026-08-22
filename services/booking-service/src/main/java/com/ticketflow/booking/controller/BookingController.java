package com.ticketflow.booking.controller;

import com.ticketflow.booking.dto.BookingResponse;
import com.ticketflow.booking.dto.CreateBookingRequest;
import com.ticketflow.booking.security.AuthenticatedUser;
import com.ticketflow.booking.security.CurrentUser;
import com.ticketflow.booking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** Convert an active seat hold into a confirmed booking. */
    @PostMapping
    public ResponseEntity<BookingResponse> confirm(
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String bearerToken
    ) {
        AuthenticatedUser user = CurrentUser.get();
        BookingResponse response = bookingService.confirm(request, user, bearerToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Customer's own booking history, newest first. */
    @GetMapping("/me")
    public Page<BookingResponse> myBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return bookingService.history(CurrentUser.get().userId(), PageRequest.of(page, size));
    }

    @GetMapping("/{bookingId}")
    public BookingResponse getOne(@PathVariable UUID bookingId) {
        return bookingService.getById(bookingId, CurrentUser.get());
    }

    /** Gate/scanner lookup — the value encoded in the QR code. */
    @GetMapping("/reference/{reference}")
    @PreAuthorize("hasAnyRole('ORGANISER','ADMIN')")
    public BookingResponse getByReference(@PathVariable String reference) {
        return bookingService.getByReference(reference);
    }

    @PatchMapping("/{bookingId}/cancel")
    public BookingResponse cancel(@PathVariable UUID bookingId) {
        return bookingService.cancel(bookingId, CurrentUser.get());
    }

    /** Public: seat IDs permanently booked for a show (overlaid on the seat map). */
    @GetMapping("/shows/{showId}/booked-seats")
    public List<UUID> bookedSeats(@PathVariable UUID showId) {
        return bookingService.bookedSeatIds(showId);
    }
}

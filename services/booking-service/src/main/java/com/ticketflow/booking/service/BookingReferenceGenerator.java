package com.ticketflow.booking.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates the customer-facing booking reference that the QR code encodes.
 *
 * <p>Format: {@code TF-XXXXXXXX} using Crockford-style base32 (no I/L/O/U) so
 * references are unambiguous when read aloud or typed by gate staff. 8 chars
 * over a 32-symbol alphabet is ~40 bits — collision-resistant enough that the
 * UNIQUE constraint on the column is a genuine last resort rather than a
 * routine retry path.
 */
@Component
public class BookingReferenceGenerator {

    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder("TF-");
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}

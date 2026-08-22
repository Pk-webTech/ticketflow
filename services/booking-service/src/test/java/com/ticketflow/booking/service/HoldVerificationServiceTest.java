package com.ticketflow.booking.service;

import com.ticketflow.booking.exception.BookingExceptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldVerificationServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    HoldVerificationService service;

    final UUID showId = UUID.randomUUID();
    final UUID seatId = UUID.randomUUID();
    final UUID holdId = UUID.randomUUID();
    final UUID customerId = UUID.randomUUID();

    String key() {
        return "seat:hold:" + showId + ":" + seatId;
    }

    @BeforeEach
    void setUp() {
        service = new HoldVerificationService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void passesWhenHoldIsLiveAndOwnedByCaller() {
        when(valueOps.get(key())).thenReturn(holdId + "|" + customerId);

        assertThatCode(() -> service.assertHoldValid(showId, List.of(seatId), holdId, customerId))
                .doesNotThrowAnyException();
    }

    /** Missing key == TTL lapsed == customer abandoned checkout. */
    @Test
    void missingKeyMeansHoldExpired() {
        when(valueOps.get(key())).thenReturn(null);

        assertThatThrownBy(() -> service.assertHoldValid(showId, List.of(seatId), holdId, customerId))
                .isInstanceOf(BookingExceptions.HoldExpiredException.class);
    }

    @Test
    void rejectsHoldOwnedByADifferentCustomer() {
        when(valueOps.get(key())).thenReturn(holdId + "|" + UUID.randomUUID());

        assertThatThrownBy(() -> service.assertHoldValid(showId, List.of(seatId), holdId, customerId))
                .isInstanceOf(BookingExceptions.NotHoldOwnerException.class);
    }

    @Test
    void rejectsMismatchedHoldIdEvenForTheSameCustomer() {
        when(valueOps.get(key())).thenReturn(UUID.randomUUID() + "|" + customerId);

        assertThatThrownBy(() -> service.assertHoldValid(showId, List.of(seatId), holdId, customerId))
                .isInstanceOf(BookingExceptions.NotHoldOwnerException.class);
    }
}
